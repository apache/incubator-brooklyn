/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.demo.webapp.hello;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.StringTokenizer;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;

public class HadoopWordCount {

    public static class TokenizerMapper extends Mapper<Object, Text, Text, IntWritable>{

        private final static IntWritable one = new IntWritable(1);
        private Text word = new Text();

        public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
            StringTokenizer itr = new StringTokenizer(value.toString());
            while (itr.hasMoreTokens()) {
                String w = itr.nextToken();
                // words (in our world) consist of [A-Za-z0-9_]+
                w = w.replaceAll("[^A-Za-z0-9_]+", " ").replaceAll("\\s+", " ").trim().toLowerCase();
                if (w.length()>0) {
                    StringTokenizer itr2 = new StringTokenizer(w);
                    while (itr2.hasMoreTokens()) {
                        String w2 = itr2.nextToken();
                        word.set(w2);
                        context.write(word, one);
                    }
                }
            }
        }
    }

    public static class IntSumReducer 
    extends Reducer<Text,IntWritable,Text,IntWritable> {
        private IntWritable result = new IntWritable();

        public void reduce(Text key, Iterable<IntWritable> values, 
                Context context
                ) throws IOException, InterruptedException {
            int sum = 0;
            for (IntWritable val : values) {
                sum += val.get();
            }
            result.set(sum);
            context.write(key, result);
        }
    }

    public static Job makeJob(Configuration conf) throws IOException {
        Job job = new Job(conf, "word count");
        job.setJarByClass(HadoopWordCount.class);
        job.setMapperClass(TokenizerMapper.class);
        job.setCombinerClass(IntSumReducer.class);
        job.setReducerClass(IntSumReducer.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(IntWritable.class);
        return job;
    }
    
    public static String stringFromInputStream(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuffer sb = new StringBuffer();
        String line;
        while ((line = reader.readLine()) != null)
            sb.append(line).append("\n");
        return sb.toString();
    }

    public static Job runWordCountWithArgs(Configuration conf, String ...args) throws FileNotFoundException, IOException, InterruptedException {
        if (args.length > 2) {
            System.err.println("Usage: wordcount [/tmp/sample.txt] [/tmp/out]");
            System.exit(2);
        }
        if (args.length > 0 && (args[0].equals("-h") || args[0].equals("--help"))) {
            System.err.println("Usage: wordcount [/tmp/sample.txt] [/tmp/out]");
            System.exit(0);
        }
        
        FileSystem fsClient = FileSystem.get(conf);
        
        // read from /tmp/sample.txt (by default), creating it if it doesn't exist
        String inN = args.length > 0 ? args[0] : "/tmp/sample.txt";
        Path in = new Path(inN);
        if (!fsClient.exists(in)) {
            FSDataOutputStream inS = fsClient.create(in);
            
            String textForInputFile = "This is a test.\nHow is this test working?\nSeems this is okay to me.\n";
            if (new File(inN).exists()) {
                //upload from local file system
                textForInputFile = stringFromInputStream(new FileInputStream(inN));
            }
            inS.write(textForInputFile.getBytes());
            inS.close();
        }
        
        // write to /tmp/out (by default), wipe it if it exists
        String outN = args.length > 1 ? args[1] : "/tmp/out";
        Path out = new Path(outN);
        if (fsClient.exists(out)) {
            fsClient.delete(out, true);
//            if (!out.delete()) {
//                Runtime.getRuntime().exec(new String[] { "rm", "-rf", "/tmp/out" }).waitFor();
//            }
        }
        
        Job job = makeJob(conf);
        FileInputFormat.addInputPath(job, new Path(inN));
        FileOutputFormat.setOutputPath(job, new Path(outN));
        return job;
    }

    public static void main(String[] args) throws Exception {
        Configuration conf = new Configuration();
//        String confFileName = System.getProperty("user.home")+"/"+".whirr"+"/"+"whirr-hadoop-sample-"+System.getProperty("user.name")+"/"+"hadoop-site.xml";
//        conf.addResource(new URL("file://"+confFileName));
//        conf.set("mapred.jar", "/tmp/jar-for-my-hadoop-app.jar");
        
        String[] otherArgs = new GenericOptionsParser(conf, args).getRemainingArgs();
        Job job = runWordCountWithArgs(conf, otherArgs);
        
        boolean result = job.waitForCompletion(true);
        if (!result) System.out.println("Job returned non-zero result code.");
        
        for (FileStatus f: FileSystem.get(conf).listStatus(new Path(args.length > 1 ? args[1] : "/tmp/out"))) {
            if (!f.isDir())
                System.out.println("Output "+f.getPath()+":\n"+stringFromInputStream(FileSystem.get(conf).open(f.getPath())));
        }
    }

}
