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
package brooklyn.util.logging;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import com.google.common.base.Throwables;

//useful for java.util.logger, which by default puts every message on two lines (horrid)
//however we don't typically use java.util.logger...
public class SimpleOneLineLogFormatter extends Formatter {

	public SimpleOneLineLogFormatter() {
		this(true, false, false);
	}

	public SimpleOneLineLogFormatter(boolean showLevel, boolean showThread, boolean showCaller) {
		this.showLevel = showLevel;
		this.showThread = showThread;
		this.showCaller = showCaller;
	}


	public final boolean showLevel;
	public final boolean showThread;
	public final boolean showCaller;

	// use shared date and formatter to minimize memory/time overhead
	protected final Date date = new Date();
	protected DateFormat dateFormat = new SimpleDateFormat(getDateFormat());

	public String getDateFormat() {
		return "yyyy-MM-dd HH:mm:ss.SSSZ";
	}

	/** uses "YYYY-DD-MM hh:mm:ss.SSS  message" format */ 
	public String format(LogRecord record) {
		StringBuffer sb = new StringBuffer();
		appendDate(record, sb);
		appendLevel(record, sb);
		sb.append("  ");
		sb.append(formatMessage(record));
		appendThreadAndCaller(record, sb);
		appendDetailsWithNewLine(sb, record);
		return sb.toString();
	}

	protected void appendLevel(LogRecord record, StringBuffer sb) {
		if (showLevel) {
			sb.append(" [").append(record.getLevel()).append("]");
		}
	}

	protected void appendDate(LogRecord record, StringBuffer sb) {
		synchronized (date) {
			date.setTime(record.getMillis());
			sb.append(dateFormat.format(date));
		}
	}

	protected void appendThreadAndCaller(LogRecord record, StringBuffer sb) {
		if (showThread || showCaller) {
			sb.append(" [");
			if (showThread)
				sb.append(getThreadName(record));
			if (showThread && showCaller) sb.append(", ");
			if (showCaller) {
				if (record.getSourceClassName() != null) {	
					sb.append(record.getSourceClassName());
				} else {
					sb.append(record.getLoggerName());
				}
				if (record.getSourceMethodName() != null) {	
					sb.append(" ");
					sb.append(record.getSourceMethodName());
				}
			}
			sb.append("]");
		}
	}

	protected void appendDetailsWithNewLine(StringBuffer sb, LogRecord record) {
		if (record.getThrown() != null) {
			try {
				sb.append('\n');
				StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw);
				record.getThrown().printStackTrace(pw);
				pw.close();
				sb.append(sw.toString());
			} catch (Exception ex) {
				//shouldn't happen with printwriter
				throw Throwables.propagate(ex);
			}
		} else {
			sb.append('\n');
		}
	}

	protected String getThreadName(LogRecord record) {
		//try to get the thread's name
		//only possible if we are the thread (unless do something messy like cache or access private fields)
		//fortunately we typically are the thread
		LogRecord lr = new LogRecord(Level.INFO, "");
		if (lr.getThreadID()==record.getThreadID())
			return Thread.currentThread().getName() + " ("+record.getThreadID()+")";
		//otherwise just say the number
		return "thread ("+record.getThreadID()+")";
	}

	public static class LogFormatterWithThreadAndCaller extends SimpleOneLineLogFormatter {
		public LogFormatterWithThreadAndCaller() {
			super(true, true, true);
		}
	}
	
}