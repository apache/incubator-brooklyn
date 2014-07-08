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
package brooklyn.event.feed;

import java.util.List;

import com.google.common.collect.ImmutableList;

/**
 * A poll handler that delegates each call to a set of poll handlers.
 * 
 * @author aled
 */
public class DelegatingPollHandler<V> implements PollHandler<V> {

    private final List<AttributePollHandler<? super V>> delegates;

    public DelegatingPollHandler(Iterable<AttributePollHandler<? super V>> delegates) {
        super();
        this.delegates = ImmutableList.copyOf(delegates);
    }

    @Override
    public boolean checkSuccess(V val) {
        for (AttributePollHandler<? super V> delegate : delegates) {
            if (!delegate.checkSuccess(val))
                return false;
        }
        return true;
    }

    @Override
    public void onSuccess(V val) {
        for (AttributePollHandler<? super V> delegate : delegates) {
            delegate.onSuccess(val);
        }
    }

    @Override
    public void onFailure(V val) {
        for (AttributePollHandler<? super V> delegate : delegates) {
            delegate.onFailure(val);
        }
    }

    @Override
    public void onException(Exception exception) {
        for (AttributePollHandler<? super V> delegate : delegates) {
            delegate.onException(exception);
        }
    }
    
    @Override
    public String toString() {
        return super.toString()+"["+getDescription()+"]";
    }
    
    @Override
    public String getDescription() {
        if (delegates.isEmpty())
            return "(empty delegate list)";
        if (delegates.size()==1) 
            return delegates.get(0).getDescription();
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        int count = 0;
        for (AttributePollHandler<? super V> delegate : delegates) {
            if (count>0) sb.append(";");
            sb.append(" ");
            sb.append(delegate.getDescription());
            if (count>2) {
                sb.append("; ...");
                break;
            }
        }
        sb.append(" ]");
        return sb.toString();
    }
    
}
