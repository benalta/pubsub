// Copyright 2016 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////
package com.google.pubsub.clients.gcloud;

import com.beust.jcommander.JCommander;
import com.google.cloud.pubsub.Message;
import com.google.cloud.pubsub.PubSub;
import com.google.cloud.pubsub.PubSubException;
import com.google.cloud.pubsub.PubSubOptions;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.pubsub.clients.common.LoadTestRunner;
import com.google.pubsub.clients.common.MetricsHandler;
import com.google.pubsub.clients.common.Task;
import com.google.pubsub.clients.common.Task.RunResult;
import com.google.pubsub.flic.common.LoadtestProto.StartRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Runs a task that publishes messages to a Cloud Pub/Sub topic.
 */
class CPSPublisherTask extends Task {
  private static final Logger log = LoggerFactory.getLogger(CPSPublisherTask.class);
  private final String topic;
  private final PubSub pubSub;
  private final String payload;
  private final int batchSize;
  private final Integer id;
  private final AtomicInteger sequenceNumber = new AtomicInteger(0);

  private CPSPublisherTask(StartRequest request) {
    super(request, "gcloud", MetricsHandler.MetricName.PUBLISH_ACK_LATENCY);
    this.pubSub = PubSubOptions.builder()
        .projectId(request.getProject())
        .build().service();
    this.topic = Preconditions.checkNotNull(request.getTopic());
    this.payload = LoadTestRunner.createMessage(request.getMessageSize());
    this.batchSize = request.getPublishBatchSize();
    this.id = (new Random()).nextInt();
  }

  public static void main(String[] args) throws Exception {
    LoadTestRunner.Options options = new LoadTestRunner.Options();
    new JCommander(options, args);
    LoadTestRunner.run(options, CPSPublisherTask::new);
  }

  @Override
  public ListenableFuture<RunResult> doRun() {
    try {
      List<Message> messages = new ArrayList<>(batchSize);
      String sendTime = String.valueOf(System.currentTimeMillis());
      for (int i = 0; i < batchSize; i++) {
        messages.add(
            Message.builder(payload)
                .addAttribute("sendTime", sendTime)
                .addAttribute("clientId", id.toString())
                .addAttribute("sequenceNumber", Integer.toString(sequenceNumber.getAndIncrement()))
                .build());
      }
      pubSub.publish(topic, messages);
      return Futures.immediateFuture(RunResult.fromBatchSize(batchSize));
    } catch (PubSubException e) {
      log.error("Publish request failed", e);
      return Futures.immediateFailedFuture(e);
    }
  }
}
