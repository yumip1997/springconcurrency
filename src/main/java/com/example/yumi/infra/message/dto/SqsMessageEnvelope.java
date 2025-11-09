package com.example.yumi.infra.message.dto;

import lombok.Getter;

@Getter
public class SqsMessageEnvelope<T> {

    private String queueName;
    private String messageGroupId;
    private String deduplicationId;
    private T payload;

    private SqsMessageEnvelope(String queueName, String messageGroupId, String deduplicationId, T payload) {
        this.queueName = queueName;
        this.messageGroupId = messageGroupId;
        this.deduplicationId = deduplicationId;
        this.payload = payload;
    }

    public static <T> SqsMessageEnvelope<T> createSqsMessageEnvelope(String queueName, String messageGroupId, String deduplicationId, T payload){
        return new SqsMessageEnvelope<>(queueName, messageGroupId, deduplicationId, payload);
    }
}
