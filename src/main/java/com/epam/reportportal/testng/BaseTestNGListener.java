/*
 * Copyright 2019 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.reportportal.testng;

import com.epam.reportportal.listeners.ItemStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.*;
import org.testng.internal.IResultListener2;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ReportPortal custom event listener. Support parallel execution of test
 * methods, suites, test classes.
 * Can be extended by providing {@link ITestNGService} implementation
 */
public class BaseTestNGListener implements IExecutionListener, ISuiteListener, IResultListener2 {

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseTestNGListener.class);

    private static final AtomicInteger INSTANCES = new AtomicInteger(0);

    private final ITestNGService testNGService;

    public BaseTestNGListener(ITestNGService testNgService) {
        this.testNGService = testNgService;
        if (INSTANCES.incrementAndGet() > 1) {
            final String warning = "WARNING! More than one ReportPortal listener is added";
            LOGGER.warn(warning);
            //even if logger is not configured, print the message to default stdout
            System.out.println(warning);
        }
    }

    @Override
    public void onExecutionStart() {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    @Override
    public void onExecutionFinish() {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    @Override
    public void onStart(ISuite suite) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    @Override
    public void onFinish(ISuite suite) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    @Override
    public void onStart(ITestContext testContext) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    @Override
    public void onFinish(ITestContext testContext) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    @Override
    public void onTestStart(ITestResult testResult) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    @Override
    public void onTestSuccess(ITestResult testResult) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    @Override
    public void onTestFailure(ITestResult testResult) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    @Override
    public void onTestSkipped(ITestResult testResult) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    @Override
    public void beforeConfiguration(ITestResult testResult) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    @Override
    public void onConfigurationFailure(ITestResult testResult) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    @Override
    public void onConfigurationSuccess(ITestResult testResult) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    @Override
    public void onConfigurationSkip(ITestResult testResult) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    // this action temporary doesn't supported by ReportPortal
    @Override
    public void onTestFailedButWithinSuccessPercentage(ITestResult result) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }
}
