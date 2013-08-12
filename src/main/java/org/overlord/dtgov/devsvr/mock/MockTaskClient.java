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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.overlord.dtgov.ui.client.shared.beans.TaskActionEnum;
import org.overlord.dtgov.ui.client.shared.beans.TaskBean;
import org.overlord.dtgov.ui.client.shared.beans.TaskInboxFilterBean;
import org.overlord.dtgov.ui.client.shared.beans.TaskInboxResultSetBean;
import org.overlord.dtgov.ui.client.shared.beans.TaskSummaryBean;
import org.overlord.dtgov.ui.server.services.tasks.ITaskClient;

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
            bean.setType("mock-task");
            if (random.nextInt() % 2 > 0) {
                bean.setDescription("Lorem ipsum dolor sit amet, consectetur adipiscing elit. Integer nec odio. Praesent libero. Sed cursus ante dapibus diam. Sed nisi. Nulla quis sem at nibh elementum imperdiet. Duis sagittis ipsum. Praesent mauris. Fusce nec tellus sed augue semper porta.");
            }
            bean.addAllowedAction(TaskActionEnum.claim);
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_YEAR, i);
            bean.setDueDate(cal.getTime());
            bean.setTaskForm(createTaskForm(i));
            bean.setTaskData(createTaskData(i));
            tasks.add(bean);
        }
    }

    /**
     * Creates a mock task form.
     * @param taskId
     */
    private static String createTaskForm(int taskId) {
        return  "<div>" +
                "<form>\r\n" +
                "  <fieldset>\r\n" +
                "    <label>Task Field 1</label>\r\n" +
                "    <input name=\"task-field-1\" type=\"text\" placeholder=\"Type something...\"></input>\r\n" +
                "    <label class=\"checkbox\">\r\n" +
                "      <input name=\"task-field-2\" type=\"checkbox\"> Task Field 2</input>\r\n" +
                "    </label>\r\n" +
                "    <label>Task Field 3</label>\r\n" +
                "    <input name=\"task-field-3\" type=\"text\"></input>\r\n" +
                "    <label>Task Field 4</label>\r\n" +
                "    <label class=\"radio\"><input type=\"radio\" name=\"task-field-4\" value=\"option-1\"></input></label>\r\n" +
                "    <input type=\"radio\" name=\"task-field-4\" value=\"option-2\">Option 2</input>\r\n" +
                "    <input type=\"radio\" name=\"task-field-4\" value=\"option-3\">Option 3</input>\r\n" +
                "    <span class=\"help-block\">Enter a short description below:</span>\r\n" +
                "    <textarea name=\"task-field-5\" rows=\"3\" cols=\"40\"></textarea>\r\n" +
                "    <label>Task Field 6</label>\r\n" +
                "    <select name=\"task-field-6\">\r\n" +
                "      <option value=\"option-1\">Option 1</option>\r\n" +
                "      <option value=\"option-2\">Option 2</option>\r\n" +
                "      <option value=\"option-3\">Option 3</option>\r\n" +
                "      <option value=\"option-4\">Option 4</option>\r\n" +
                "    </select>\r\n" +
                "    <label>Read-Only</label>\r\n" +
                "    <div>\r\n" +
                "      We support:\r\n" +
                "      <p>\r\n" +
                "        spans: <span data-name=\"task-label-1\"></span>\r\n" +
                "      </p>\r\n" +
                "      <p>\r\n" +
                "        divs: <div data-name=\"task-label-2\"></div>\r\n" +
                "      </p>\r\n" +
                "      <p>\r\n" +
                "        labels: <label data-name=\"task-label-3\"></label>\r\n" +
                "      </p>\r\n" +
                "    </div>\r\n" +
                "  </fieldset>\r\n" +
                "</form>\r\n" +
                "" +
                "</div>";
    }

    /**
     * Creates mock task data.
     * @param taskId
     */
    private static Map<String, String> createTaskData(int taskId) {
        Map<String, String> data = new HashMap<String, String>();
        data.put("TaskName", "sample-task");
        data.put("task-field-1", "Hello World");
        data.put("task-field-2", "true");
        data.put("task-field-3", "Foo Bar");
        data.put("task-field-4", "option-2");
        data.put("task-field-5", "Creates a mock task form.");
        data.put("task-field-6", "option-3");
        data.put("task-label-1", "Span Label");
        data.put("task-label-2", "Div Label");
        data.put("task-label-3", "Label Label");
        return data;
    }

    /**
     * Constructor.
     */
    public MockTaskClient() {
    }

    /**
     * @see org.overlord.dtgov.ui.server.services.tasks.ITaskClient#getTasks(org.overlord.dtgov.ui.client.shared.beans.TaskInboxFilterBean, int, int)
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
     * @see org.overlord.dtgov.ui.server.services.tasks.ITaskClient#getTask(java.lang.String)
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
     * @see org.overlord.dtgov.ui.server.services.tasks.ITaskClient#updateTask(org.overlord.dtgov.ui.client.shared.beans.TaskBean)
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
     * @see org.overlord.dtgov.ui.server.services.tasks.ITaskClient#executeAction(org.overlord.dtgov.ui.client.shared.beans.TaskBean, org.overlord.dtgov.ui.client.shared.beans.TaskActionEnum)
     */
    @Override
    public TaskBean executeAction(TaskBean task, TaskActionEnum action) throws Exception {
        TaskBean ptask = getTask(task.getId());
        if (!task.isActionAllowed(action)) {
            throw new Exception("Action not allowed.");
        }
        if (action == TaskActionEnum.claim) {
            doAction(ptask, "currentuser", MockTaskStatus.Reserved, TaskActionEnum.release,
                    TaskActionEnum.start, TaskActionEnum.fail, TaskActionEnum.complete);
        } else if (action == TaskActionEnum.release) {
            doAction(ptask, null, MockTaskStatus.Ready, TaskActionEnum.claim);
        } else if (action == TaskActionEnum.start) {
            doAction(ptask, "currentuser", MockTaskStatus.InProgress, TaskActionEnum.stop, TaskActionEnum.release, TaskActionEnum.complete, TaskActionEnum.fail);
        } else if (action == TaskActionEnum.stop) {
            doAction(ptask, "currentuser", MockTaskStatus.Reserved, TaskActionEnum.release,
                    TaskActionEnum.start, TaskActionEnum.fail, TaskActionEnum.complete);
        } else if (action == TaskActionEnum.complete) {
            doAction(ptask, null, MockTaskStatus.Completed);
            ptask.setTaskData(task.getTaskData());
        } else if (action == TaskActionEnum.fail) {
            doAction(ptask, null, MockTaskStatus.Failed);
            ptask.setTaskData(task.getTaskData());
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

    /**
     * @see org.overlord.dtgov.ui.server.services.tasks.ITaskClient#setLocale(java.util.Locale)
     */
    @Override
    public void setLocale(Locale locale) {
        // No locale support in the mock task client. :)
    }

}
