package com.example.wms.kafka;

import com.example.wms.event.WarehouseQueryEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WarehouseEventProducer {

    private static final String TOPIC = "warehouse-query";

    private final KafkaTemplate<String, WarehouseQueryEvent> kafkaTemplate;

    public void sendQueryEvent(WarehouseQueryEvent event){
        kafkaTemplate.send(TOPIC, event).whenComplete((result, ex) -> {
            if (null == ex){
                log.info("Kafka消息发送成功 -> Topic: {}, Partition: {}, Offset: {}, 仓库数量: {}",
                        TOPIC,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        event.getWarehouseCount());
            }
            else {
                log.error("Kafka消息发送失败", ex);
            }
        });

    }
}
