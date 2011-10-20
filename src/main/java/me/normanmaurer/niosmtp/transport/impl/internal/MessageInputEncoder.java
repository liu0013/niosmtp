/**
* Licensed to niosmtp developers ('niosmtp') under one or more
* contributor license agreements. See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* niosmtp licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License. You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package me.normanmaurer.niosmtp.transport.impl.internal;

import java.io.InputStream;
import java.util.Set;

import me.normanmaurer.niosmtp.ByteArrayMessageInput;
import me.normanmaurer.niosmtp.MessageInput;
import me.normanmaurer.niosmtp.SMTPClientConstants;
import me.normanmaurer.niosmtp.core.DataTerminatingInputStream;
import me.normanmaurer.niosmtp.transport.SMTPClientSession;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.oneone.OneToOneEncoder;
import org.jboss.netty.handler.stream.ChunkedStream;

/**
 * {@link OneToOneEncoder} which encodes the {@link MessageInput} to a {@link ChunkedStream}
 * 
 * @author Norman Maurer
 *
 */
class MessageInputEncoder extends OneToOneEncoder implements SMTPClientConstants{

    private final SMTPClientSession session;
    
    private final static byte CR = '\r';
    private final static byte LF = '\n';
    private final static byte DOT = '.';
    private final static byte[] DOT_CRLF = new byte[] {DOT, CR, LF};
    private final static byte[] CRLF_DOT_CRLF = new byte[] {CR, LF, DOT, CR, LF};
    private final static byte[] LF_DOT_CRLF = new byte[] {LF, DOT, CR, LF};
    
    public MessageInputEncoder(final SMTPClientSession session) {
        this.session = session;
    }
    
    @Override
    protected Object encode(ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception {
        if (msg instanceof MessageInput){
            MessageInput input = (MessageInput) msg;
            Set<String> extensions = session.getSupportedExtensions();
            if (msg instanceof ByteArrayMessageInput) {
                byte[] data;
                
                if (extensions.contains(_8BITMIME_EXTENSION)) {
                    data = ((ByteArrayMessageInput)input).get8BitAsByteArray();
                } else {
                    data = ((ByteArrayMessageInput)input).get7BitAsByteArray();
                }
                return createDataTerminatingChannelBuffer(data);
            } else {
                InputStream msgIn;
                
                if (extensions.contains(_8BITMIME_EXTENSION)) {
                    msgIn = input.get8Bit();
                } else {
                    msgIn = input.get7bit();
                }
                       
                return new ChunkedStream(new DataTerminatingInputStream(msgIn));
            }

        } else {
            return msg;
        }
    }
    
    /**
     * Create a {@link ChannelBuffer} which is terminated with a CRLF.CRLF sequence
     * 
     * @param data
     * @return buffer
     */
    private static ChannelBuffer createDataTerminatingChannelBuffer(byte[] data) {
        int length = data.length;
        if (length < 1) {
            return ChannelBuffers.wrappedBuffer(CRLF_DOT_CRLF);
        } else {
            byte[] terminating;

            byte last = data[length -1];

            if (length == 1) {
                if (last == CR) {
                    terminating = LF_DOT_CRLF;
                } else {
                    terminating = CRLF_DOT_CRLF;
                }
            } else {
                byte prevLast = data[length - 2];
                
                if (last == LF) {
                    if (prevLast == CR) {
                        terminating = DOT_CRLF;
                    } else {
                        terminating = CRLF_DOT_CRLF;
                    }
                } else if (last == CR) {
                    terminating = LF_DOT_CRLF;
                } else {
                    terminating = CRLF_DOT_CRLF;

                }
            }
            return ChannelBuffers.wrappedBuffer(data, terminating);
        }
        
      
    }

}
