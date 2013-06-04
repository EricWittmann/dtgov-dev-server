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

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.overlord.dtgov.ui.client.shared.beans.TaskActionEnum;
import org.overlord.dtgov.ui.client.shared.beans.TaskBean;
import org.overlord.dtgov.ui.client.shared.beans.TaskInboxFilterBean;
import org.overlord.dtgov.ui.client.shared.beans.TaskInboxResultSetBean;
import org.overlord.dtgov.ui.client.shared.beans.TaskSummaryBean;
import org.overlord.dtgov.ui.server.api.ITaskClient;

/**
 * A mock task client that provides sample data.
 * @author eric.wittmann@redhat.com
 */
public class MockTaskClient implements ITaskClient {

    private final static String secureRandomAlgorithm = "SHA1PRNG";
    private final static SecureRandom random;

    private static List<TaskBean> tasks = new ArrayList<TaskBean>();
    static {
        try {
            random = SecureRandom.getInstance(secureRandomAlgorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("runtime does not support secure random algorithm: " + secureRandomAlgorithm);
        }
        for (int i=0; i < 42; i++) {
            TaskBean bean = new TaskBean();
            bean.setId(String.valueOf(i));
            bean.setName("Task " + i);
            bean.setOwner((random.nextInt(111) % 4) == 0 ? "ewittman" : null);
            bean.setPriority(random.nextInt(3));
            bean.setStatus(MockTaskStatus.Ready.toString());
            if (random.nextInt() % 2 > 0) {
                bean.setDescription("Lorem ipsum dolor sit amet, consectetur adipiscing elit. Integer nec odio. Praesent libero. Sed cursus ante dapibus diam. Sed nisi. Nulla quis sem at nibh elementum imperdiet. Duis sagittis ipsum. Praesent mauris. Fusce nec tellus sed augue semper porta.");
            }
            bean.setGroup("developers");
            bean.addAllowedAction(TaskActionEnum.claim);
            bean.addAllowedAction(TaskActionEnum.save);
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_YEAR, i);
            bean.setDueDate(cal.getTime());
            tasks.add(bean);
        }
    }

    /**
     * Constructor.
     */
    public MockTaskClient() {
    }

    /**
     * @see org.overlord.dtgov.ui.server.api.ITaskClient#getTasks(org.overlord.dtgov.ui.client.shared.beans.TaskInboxFilterBean, int, int)
     */
    @Override
    public TaskInboxResultSetBean getTasks(TaskInboxFilterBean filters, int startIndex, int endIndex) {
        List<TaskSummaryBean> filteredTasks = new ArrayList<TaskSummaryBean>();
        for (TaskSummaryBean task : tasks) {
            if (matchesFilter(filters, task)) {
                filteredTasks.add(task);
            }
        }

        TaskInboxResultSetBean result = new TaskInboxResultSetBean();
        result.setItemsPerPage((endIndex-startIndex)+1);
        result.setStartIndex(startIndex);
        result.setTasks(new ArrayList<TaskSummaryBean>(filteredTasks.subList(startIndex, Math.min(endIndex+1, filteredTasks.size()))));
        result.setTotalResults(filteredTasks.size());
        return result;
    }

    /**
     * @param filters
     * @param task
     */
    private boolean matchesFilter(TaskInboxFilterBean filters, TaskSummaryBean task) {
        if (filters.getPriority() >= 0) {
            if (task.getPriority() != filters.getPriority()) {
                return false;
            }
        }
        if (filters.getDateDueFrom() != null && task.getDueDate() != null) {
            if (task.getDueDate().compareTo(filters.getDateDueFrom()) < 0) {
                return false;
            }
        }
        if (filters.getDateDueTo() != null && task.getDueDate() != null) {
            if (task.getDueDate().compareTo(filters.getDateDueTo()) > 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * @see org.overlord.dtgov.ui.server.api.ITaskClient#getTask(java.lang.String)
     */
    @Override
    public TaskBean getTask(String taskId) {
        for (TaskBean task : tasks) {
            if (task.getId().equals(taskId)) {
                return task;
            }
        }
        return null;
    }

    /**
     * @see org.overlord.dtgov.ui.server.api.ITaskClient#updateTask(org.overlord.dtgov.ui.client.shared.beans.TaskBean)
     */
    @Override
    public void updateTask(TaskBean task) {
        TaskBean ptask = getTask(task.getId());
        if (ptask != null) {
            ptask.setDescription(task.getDescription());
            ptask.setPriority(task.getPriority());
        }
    }

    /**
     * @see org.overlord.dtgov.ui.server.api.ITaskClient#executeAction(org.overlord.dtgov.ui.client.shared.beans.TaskBean, org.overlord.dtgov.ui.client.shared.beans.TaskActionEnum)
     */
    @Override
    public TaskBean executeAction(TaskBean task, TaskActionEnum action) throws Exception {
        TaskBean ptask = getTask(task.getId());
        if (!task.isActionAllowed(action)) {
            throw new Exception("Action not allowed.");
        }
        if (action == TaskActionEnum.claim) {
            doAction(ptask, "currentuser", MockTaskStatus.Reserved, TaskActionEnum.release,
                    TaskActionEnum.start, TaskActionEnum.save, TaskActionEnum.fail, TaskActionEnum.complete);
        } else if (action == TaskActionEnum.release) {
            doAction(ptask, null, MockTaskStatus.Ready, TaskActionEnum.claim, TaskActionEnum.save);
        } else if (action == TaskActionEnum.start) {
            doAction(ptask, null, MockTaskStatus.InProgress, TaskActionEnum.stop, TaskActionEnum.release, TaskActionEnum.save, TaskActionEnum.complete, TaskActionEnum.fail);
        } else if (action == TaskActionEnum.stop) {
            doAction(ptask, null, MockTaskStatus.Reserved, TaskActionEnum.release,
                    TaskActionEnum.start, TaskActionEnum.save, TaskActionEnum.fail, TaskActionEnum.complete);
        } else if (action == TaskActionEnum.complete) {
            doAction(ptask, null, MockTaskStatus.Completed);
        } else if (action == TaskActionEnum.fail) {
            doAction(ptask, null, MockTaskStatus.Failed);
        } else {
            throw new Exception("Action " + action + " not supported.");
        }
        return ptask;
    }

    /**
     * @param task
     */
    private void doAction(TaskBean task, String owner, MockTaskStatus status, TaskActionEnum ... actions) {
        task.setOwner(owner);
        task.setStatus(status.toString());
        task.getAllowedActions().clear();
        for (TaskActionEnum action : actions) {
            task.addAllowedAction(action);
        }
    }

}
