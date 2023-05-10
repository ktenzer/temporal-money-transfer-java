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

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class AccountTransferWorkflowImpl implements AccountTransferWorkflow {
  private final RetryOptions retryOptionsSingleRetry =
      RetryOptions.newBuilder()
          .setInitialInterval(Duration.ofSeconds(5))
          .setMaximumInterval(Duration.ofSeconds(100))
          .setBackoffCoefficient(2)
          .setMaximumAttempts(1)
          .build();
  private final ActivityOptions optionsSingleRetry =
      ActivityOptions.newBuilder()
          .setRetryOptions(retryOptionsSingleRetry)
          .setStartToCloseTimeout(Duration.ofSeconds(5))
          .build();

  // these options are for retrying activities
  private final RetryOptions retryOptionsMultipleRetries =
      RetryOptions.newBuilder()
          .setInitialInterval(Duration.ofSeconds(3))
          .setMaximumInterval(Duration.ofSeconds(100))
          .setBackoffCoefficient(1)
          .setMaximumAttempts(5)
          .build();

  private final ActivityOptions optionsMultipleRetries =
      ActivityOptions.newBuilder()
          .setRetryOptions(retryOptionsMultipleRetries)
          .setStartToCloseTimeout(Duration.ofSeconds(5))
          .build();

  // uncomment me for the default workflow (only trying services once)
  private final TransferService transferService =
      Workflow.newActivityStub(TransferService.class, optionsSingleRetry);

  // uncomment me for the workflow that makes multiple withdraw activity attempts
  private final TransferService transferServiceMultipleRetries =
      Workflow.newActivityStub(TransferService.class, optionsMultipleRetries);

  private static void printAccountBalances(String header, Account fromAccount, Account toAccount) {
    System.out.println();
    System.out.println(header);
    System.out.println("From Account: " + fromAccount.toString());
    System.out.println("To Account: " + toAccount.toString());
  }

  @Override
  public String transfer(
      Account fromAccount,
      Account toAccount,
      String referenceId,
      int amountDollars,
      boolean simulateDepositRetries) {

    // use these to slow down the workflow for demos
    Duration shortTimer = Duration.ofSeconds(3);
    Duration longTimer = Duration.ofSeconds(6);

    List<String> compensations = new ArrayList<>();
    try {

      System.out.printf(
          "\n\nTransfer workflow STARTED ($%d from %s to %s [%s])\n",
          amountDollars, fromAccount.getAccountId(), toAccount.getAccountId(), referenceId);

      // print starting balance
      Workflow.sleep(shortTimer); // simulated delay
      printAccountBalances("Starting Balance", fromAccount, toAccount);

      Workflow.sleep(shortTimer); // simulated delay

      // withdraw from fromAccount
      System.out.printf(
          "\n\nWithdrawing $%d from account %s (please wait..)\n\n",
          amountDollars, fromAccount.getAccountId());

      Workflow.sleep(shortTimer); // simulated delay

      fromAccount = transferService.withdraw(fromAccount, referenceId, amountDollars);
      compensations.add("undo_withdraw");

      Workflow.sleep(longTimer); // simulated delay
      printAccountBalances("Withdrawal Done", fromAccount, toAccount);
      Workflow.sleep(longTimer); // simulated delay

      // deposit to toAccount
      System.out.printf(
          "\n\nDepositing $%d to account %s (please wait)\n\n",
          amountDollars, toAccount.getAccountId());
      Workflow.sleep(shortTimer); // simulated delay

      // this code path is for the workflow that makes multiple withdraw activity attempts
      if (simulateDepositRetries) {
        toAccount =
            transferServiceMultipleRetries.deposit(
                toAccount, referenceId, amountDollars, simulateDepositRetries);
      } else {
        toAccount =
            transferService.deposit(toAccount, referenceId, amountDollars, simulateDepositRetries);
      }
      compensations.add("undo_deposit");

      Workflow.sleep(longTimer); // simulated delay
      printAccountBalances("Deposit Done", fromAccount, toAccount);

    } catch (ActivityFailure e) {
      for (int i = compensations.size() - 1; i >= 0; i--) {
        Workflow.sleep(shortTimer); // simulated delay
        String compensation = compensations.get(i);
        if ("undo_deposit".equals(compensation)) {
          System.out.printf(
              "\n\nUndoing deposit to account %s (check API response)\n\n",
              toAccount.getAccountId());
          Workflow.sleep(longTimer); // simulated delay

          toAccount = transferService.undoDeposit(toAccount, referenceId, amountDollars);

          Workflow.sleep(shortTimer); // simulated delay

        } else if ("undo_withdraw".equals(compensation)) {
          System.out.printf(
              "\n\nUndoing withdrawal from account %s (check API response)\n\n",
              fromAccount.getAccountId());
          Workflow.sleep(longTimer); // simulated delay

          fromAccount = transferService.undoWithdraw(fromAccount, referenceId, amountDollars);

          Workflow.sleep(shortTimer); // simulated delay
        }
      }
      Workflow.sleep(shortTimer); // simulated delay

      printAccountBalances(
          "Transfer Rollback Complete. Final Account Balances", fromAccount, toAccount);

      System.out.println("Workflow FAILED, check logs.");
      return "FAIL";
    }
    Workflow.sleep(shortTimer); // simulated delay

    System.out.printf(
        "\n\nTransferred $%d from %s to %s [%s])\n",
        amountDollars, fromAccount.getAccountId(), toAccount.getAccountId(), referenceId);
    printAccountBalances("Final Account Balances", fromAccount, toAccount);

    System.out.println("Workflow finished successfully.");

    return "SUCCESS";
  }
}
