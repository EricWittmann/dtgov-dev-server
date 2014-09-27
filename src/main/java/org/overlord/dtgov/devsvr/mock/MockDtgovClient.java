/*
 * Copyright 2013 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.overlord.dtgov.devsvr.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.overlord.dtgov.common.model.Deployer;
import org.overlord.dtgov.ui.server.services.dtgov.IDtgovClient;

/**
 * Mock version of the dtgov client.
 *
 * @author eric.wittmann@redhat.com
 */
public class MockDtgovClient implements IDtgovClient {
    
    /**
     * Constructor.
     */
    public MockDtgovClient() {
    }

    /**
     * @see org.overlord.dtgov.ui.server.services.dtgov.IDtgovClient#stopProcess(java.lang.String, long)
     */
    @Override
    public void stopProcess(String targetUUID, long processId) throws Exception {
    }

    /**
     * @see org.overlord.dtgov.ui.server.services.dtgov.IDtgovClient#getCustomDeployerNames()
     */
    @Override
    public List<Deployer> getCustomDeployerNames() throws Exception {
        return new ArrayList<Deployer>();
    }

    /**
     * @see org.overlord.dtgov.ui.server.services.dtgov.IDtgovClient#setLocale(java.util.Locale)
     */
    @Override
    public void setLocale(Locale locale) {
    }

}
