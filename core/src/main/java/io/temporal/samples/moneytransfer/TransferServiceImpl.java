/*
 *  Copyright (c) 2020 Temporal Technologies, Inc. All Rights Reserved
 *
 *  Copyright 2012-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *  Modifications copyright (C) 2017 Uber Technologies, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"). You may not
 *  use this file except in compliance with the License. A copy of the License is
 *  located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 *  or in the "license" file accompanying this file. This file is distributed on
 *  an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 */

package io.temporal.samples.moneytransfer;

import io.temporal.activity.Activity;
import io.temporal.activity.ActivityExecutionContext;
import io.temporal.activity.ActivityInfo;
import io.temporal.failure.ApplicationFailure;
import io.temporal.samples.moneytransfer.dataclasses.ChargeResponse;
import io.temporal.samples.moneytransfer.web.ServerInfo;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class TransferServiceImpl implements TransferService {
  private static final Logger log = LoggerFactory.getLogger(TransferServiceImpl.class);

  @Override
  public ChargeResponse createCharge(String idempotencyKey, float amountCents) {

    ActivityExecutionContext ctx = Activity.getExecutionContext();
    ActivityInfo info = ctx.getInfo();

    if (info.getAttempt() < 5) {
      log.info("\n*** RETRY ATTEMPT: " + info.getAttempt() + "***\n");
      int delaySeconds = 7;
      log.info("\n\n/API/simulateDelay Seconds" + delaySeconds + "\n");
      String delayResponse = simulateDelay(delaySeconds);
      log.info("\n\n/API/simulateDelay Response" + delayResponse + "\n");

    }

    log.info("\n\n/API/charge\n");
    if (amountCents > 1000) {
      throw ApplicationFailure.newNonRetryableFailure(
          "Insufficient Funds", "createCharge Activity Failed");
    }

    ChargeResponse response = new ChargeResponse("example-charge-id");

    return response;
  }

  private static String simulateDelay(int seconds) {
    String url = ServerInfo.getWebServerURL() + "/simulateDelay?s=" + seconds;
    log.info("\n\n/API/simulateDelay URL: " + url + "\n");
    Request request = new Request.Builder().url(url).build();
    try (Response response = new OkHttpClient().newCall(request).execute()) {
      return response.body().string();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
