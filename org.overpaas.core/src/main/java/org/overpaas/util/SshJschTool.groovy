package org.overpaas.util;

import org.apache.ivy.Main;

import com.jcraft.jsch.*;

/** class is a wrapper for jsch session, taken from their examples */
public class SshJschTool {

	String host, 
		user = System.getProperty('user.name');
	def port = 22,
		keyFiles = ['~/.ssh/id_dsa','~/.ssh/id_rsa'],
		config = ['StrictHostKeyChecking', 'no']
	
	/** tidies up fields and config, e.g. replacing leading '~' with System.getProperty('user.home'),
	 * replacing 'user@host' in host by setting username
	 */
	public void tidy() {
		initJsch();
		if (host && host==~ /[^@]+@[^@]+/) {
			(user,host) = (host=~/([^@]+)@([^@]+)/)[0][1,2]
		}
		keyFiles = keyFiles.collect { it.startsWith('~')? (System.getProperty('user.home')+it.substring(1)) : it }
	}

	private JSch jsch;
	private Session session;	
	
	public void initJsch() {
		if (!jsch) jsch=new JSch();
	}
	
	public JSch getJsch() { jsch }
	public Session getSession() { session }
	
	public void connect() {
		if (session && session.isConnected()) throw new IllegalStateException("already connected to "+session)
		if (!host) throw new IllegalStateException("host must be specified")
		
		tidy()
		
		keyFiles.each { if (new File(it).exists()) { jsch.addIdentity(it)
            println it
            } }
		
	    session = jsch.getSession(user, host, port)
		session.setConfig(config)
		try{			
			session.connect()
		} catch (Exception e) {
			throw new IllegalStateException("Cannot connect to $user@$host: " + e);
		}
	}
	public void disconnect() {
		session.disconnect()
		session = null
	}
	public boolean isConnected() {
		session && session.isConnected()
	}

	protected void assertConnected() {
		if (!session) throw new IllegalStateException("no ssh session yet; invoke connect() before using")
		if (!isConnected()) throw new IllegalStateException("ssh session disconnected")
		assert session && session.isConnected() : ""
	}
	
	public static void block(Channel c, int pollPeriodMillis=50) {
		//TODO would much prefer to join the thread, but that isn't exposed!
		while (!c.isClosed()) synchronized (c) { c.wait(pollPeriodMillis) }
	}
	
	/** executes the set of commands in a shell; optional property 'out' should be an output stream;
	 * blocks until completion (unless property 'block' set as false), so you must send an exit command;
	 * returns exit status */
	public int execShell(Map properties=[:], String ...commands) {
		assertConnected()
		ChannelShell channel=session.openChannel("shell");
		lastChannel = channel
		if (properties.out) channel.setOutputStream properties.out, true
		StringBuffer sb = []
		commands.each { sb.append(it); sb.append("\n") }
		channel.setInputStream new ByteArrayInputStream( sb.toString().getBytes() )
 
		channel.connect()
		if (properties.block==null || properties.block) block(channel)
		channel.getExitStatus()
	}	

	/** convenience for the last channel used, in case it is needed */
	public Channel lastChannel;
	
	/** executes the set of commands using ssh exec, ";" separated (overridable with property 'separator'; optional properties 'out' and 'err' should be streams;
	 * this is generally preferable to shell because it captures both streams and doesn't need an explicit exit,
	 * but may cause problems if you are doing funny escaping or need env values which are only set on a full-fledged shell;
	 * returns exit status (if blocking)  */
	public int execCommands(Map properties=[:], String ...commands) {
		assertConnected()
		ChannelExec channel=session.openChannel("exec");
		lastChannel = channel;
		if (properties.out) channel.setOutputStream properties.out, true
		if (properties.err) channel.setErrStream properties.err, true
		String separator = properties.separator ?: " ; "
		channel.setCommand Arrays.asList(commands).join(separator)
 
		channel.connect()
		if (properties.block==null || properties.block) block(channel)
		channel.getExitStatus()
	}

	
	static int checkAck(InputStream ins) throws IOException{
	  int b=ins.read();
	  // b may be 0 for success,
	  //          1 for error,
	  //          2 for fatal error,
	  //          -1 ???
	  if(b==0) return 0;  
	  if(b==1 || b==2){
		StringBuffer sb=new StringBuffer();
		int c;
		while (c!='\n') {
		  c=ins.read();
		  sb.append((char)c);
		}
		while(c!='\n');
		if(b==1) throw new IOException("ssh server indicated error: "+sb)
		if(b==2) throw new IOException("ssh server indicated fatal error: "+sb)
	  }
	  throw new IOException("ssh server failed to ack appropriately ("+b+")")
	}
	
	/** properties can be:
	 *  permissions (must be four-digit octal string, default '0644');
	 *  lastModificationDate (should be UTC/1000, ie seconds since 1970; defaults to current);
	 *  lastAccessDate (again UTC/1000; defaults to lastModificationDate);
	 *  [if neither lastXxxDate set it does not send that line (unless property ptimestamp set true)]
	 * @param input
	 * @param size
	 * @param pathAndFileOnRemoteServer
	 */
	public void createFile(Map p=[:], String pathAndFileOnRemoteServer, InputStream input, long size) {
		assertConnected()
		ChannelExec channel=session.openChannel("exec");
		lastChannel = channel;
		
		int targetSepIndex = pathAndFileOnRemoteServer.lastIndexOf('/');
		String targetName = pathAndFileOnRemoteServer.substring(targetSepIndex+1)
		String targetPath = targetSepIndex>=0 ? pathAndFileOnRemoteServer.substring(0, targetSepIndex) : "."
		
		boolean ptimestamp = (p.timestamp!=null ? p.timestamp : p.lastModificationDate || p.lastAccessDate);
		String command = "scp " + (ptimestamp ? "-p " :"") + "-t "+targetPath
		channel.setCommand command
//		println "connecting, with command $command"
		channel.connect()
		InputStream fromChannel = channel.getInputStream()
		OutputStream toChannel = channel.getOutputStream()
		checkAck(fromChannel)
		
		if (p.lastModificationDate || p.lastAccessDate) {
			long lmd = p.lastModificationDate ?: System.currentTimeMillis()
			long lad = p.lastAccessDate ?: lmd
//			println "sending mod date"
			toChannel << "T "+(lmd/1000)+" 0 "+(lad/1000)+" 0\n"
			toChannel.flush()
			checkAck(fromChannel)
		}
//		 send "C0644 filesize filename", where filename should not include '/'
		command = "C"+(p.permissions ?: '0644') + " "+size+" "+targetName+"\n"
//		println "sending file init $command"
		toChannel << command.getBytes();
		toChannel.flush()
		checkAck(fromChannel)
		
		byte[] buf = new byte[1024];
		while (size>0) {
			int numRead = input.read(buf, 0, (int)(size>1024?1024:size));
			if (numRead <= 0) throw new IOException("error reading from input when copying to "+pathAndFileOnRemoteServer+" at "+session)
			size -= numRead;
//			println "read $numRead bytes, now sending, size now $size"
			toChannel.write buf, 0, numRead
		}
		toChannel.write 0
		toChannel.flush()
		checkAck(fromChannel)
		toChannel.close()
		channel.disconnect()
	}

	/** creates the given file with the given contents; permissions specified using 'permissions:0755' */
	public void createFile(Map p=[:], String pathAndFileOnRemoteServer, String contents) {
		byte[] b = contents.getBytes()
		createFile(p, pathAndFileOnRemoteServer, new ByteArrayInputStream(b), b.length)
	}
	/** creates the given file with the given contents; permissions specified using 'permissions:0755' */
	public void createFile(Map p=[:], String pathAndFileOnRemoteServer, byte[] contents) {
		createFile(p, pathAndFileOnRemoteServer, new ByteArrayInputStream(contents), contents.length)
	}

	/** copies file (but won't preserve permission of last _access_ date since these not available in java
	* (last mod date is fine); if path is null, empty, '.', '..', or ends with '/' then file name is used
	* <p>
	* to set permissions (or override mod date) use 'permissions:"0644"', as described at {@link #copyTo(Map, InputStream, int, String)}
	* @param file
	* @param pathAndFileOnRemoteServer
	*/
	public void copyToServer(Map p=[:], File f, String pathAndFileOnRemoteServer=null) {
		def p2 = [lastModificationDate:f.lastModified()]
		p2 << p
		String fn = pathAndFileOnRemoteServer
		if (fn==null) fn=""
		if (fn=="." || fn=="..") fn+="/";
		if (!fn || fn.isEmpty() || fn.endsWith("/")) fn+=f.getName()

		createFile(p2, fn, new FileInputStream(f), f.size())
	}

	//TODO copy from
/*

    // exec 'scp -f rfile' remotely
      ((ChannelExec)channel).setCommand(command);

      // get I/O streams for remote scp
      OutputStream out=channel.getOutputStream();
      InputStream in=channel.getInputStream();

      channel.connect();

      // send '\0'
      buf[0]=0; out.write(buf, 0, 1); out.flush();

      while(true){
      	int c=checkAck(in);
        if(c!='C'){
          break;
        }

        // read permissions ('0644 ')
        in.read(buf, 0, 5);

		// filesize, name
        long filesize=0L;
        while(true){
          if(in.read(buf, 0, 1)<0){
            // error
            break; 
          }
          if(buf[0]==' ')break;
          filesize=filesize*10L+(long)(buf[0]-'0');
        }

        String file=null;
        for(int i=0;;i++){
          in.read(buf, i, 1);
          if(buf[i]==(byte)0x0a){
            file=new String(buf, 0, i);
            break;
          }
        }

        // send ack '\0'
        buf[0]=0; out.write(buf, 0, 1); out.flush();

        // read contents
        fos=new FileOutputStream(prefix==null ? lfile : prefix+file);
        int foo;
        while(true){
          if(buf.length<filesize) foo=buf.length;
          else foo=(int)filesize;
          foo=in.read(buf, 0, foo);
          if(foo<0){
            // error 
            break;
          }
          fos.write(buf, 0, foo);
          filesize-=foo;
          if(filesize==0L) break;
        }
        fos.close();
        fos=null;

        if(checkAck(in)!=0){
          System.exit(0);
        }

        // send '\0'
        buf[0]=0; out.write(buf, 0, 1); out.flush();
      }

      session.disconnect();
 */
	
	
	public static void main(String ...args) {
		def t = new SshJschTool(host:'localhost')
		t.connect()

		t.createFile "/tmp/say-hello.sh", "echo hello world!\n"
		t.execCommands "mkdir -p /tmp/exec"
		t.copyToServer permissions:'0755', new File("/tmp/say-hello.sh"), "/tmp/exec/"
		t.execShell out:System.out, "cat /tmp/exec/say-hello.sh", "ls -al /tmp/exec/say-hello.sh", "/tmp/exec/say-hello.sh", "exit"

		t.disconnect()
	}
}
