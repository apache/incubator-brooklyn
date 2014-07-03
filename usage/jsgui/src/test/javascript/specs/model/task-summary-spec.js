/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
*/
define([
    "model/task-summary"
], function (TaskSummary) {

    describe("model/task-summary spec", function () {
        var tasks = new TaskSummary.Collection
        tasks.url = "fixtures/task-summary-list.json"
        tasks.fetch({async:false})

        it("loads the collection from 'fixtures/task-summary-list.json'", function () {
            var task = tasks.at(0)
            expect(task.get("entityId")).toBe("VzK45RFC")
            expect(task.get("displayName")).toBe("start")
            expect(task.get("rawSubmitTimeUtc")).toBe(1348663165550)
        })
    })
})