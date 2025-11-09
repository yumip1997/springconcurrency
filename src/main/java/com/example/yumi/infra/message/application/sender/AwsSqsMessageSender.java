package com.example.yumi.infra.message.application.sender;

import com.example.yumi.common.utils.ObjectMapperUtil;
import com.example.yumi.infra.message.dto.SqsMessageEnvelope;
import io.awspring.cloud.sqs.operations.SendResult;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AwsSqsMessageSender {

    private final SqsTemplate sqsTemplate;

    public <T> SendResult<T> sendMessage(SqsMessageEnvelope<T> request){
        SendResult<T> result = sqsTemplate.send(to -> to
                .queue(request.getQueueName())
                .messageGroupId(request.getMessageGroupId())
                .messageDeduplicationId(Optional.ofNullable(request.getDeduplicationId()).orElse(UUID.randomUUID().toString()))
                .payload(request.getPayload()));

        log.info("send to sqs messageId : {}", result.messageId());

        return result;
    }
}
