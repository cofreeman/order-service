package com.example.orderservice.controller;

import com.example.orderservice.dto.OrderDto;
import com.example.orderservice.entity.OrderEntity;
import com.example.orderservice.messagequeue.KafkaProducer;
import com.example.orderservice.messagequeue.OrderProducer;
import com.example.orderservice.service.OrderService;
import com.example.orderservice.vo.RequestOrder;
import com.example.orderservice.vo.ResponseOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/order-service")
@Slf4j
public class OrderController {
    private final Environment environment;
    private final OrderService orderService;
    private final KafkaProducer kafkaProducer;
    private final OrderProducer orderProducer;

    @GetMapping("/health_check")
    public String status(){
        return String.format("It's Ok %s",environment.getProperty("local.server.port"));
    }

    @PostMapping("/{userId}/orders")
    public ResponseEntity<ResponseOrder> createOrder(@RequestBody RequestOrder order,
                                                     @PathVariable String userId){
        log.info("Before add orders data");
        ModelMapper modelMapper = new ModelMapper();
        modelMapper.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);

        OrderDto orderDto = modelMapper.map(order, OrderDto.class);
        orderDto.setUserId(userId);

        /* jpa */
//        orderService.createOrder(orderDto);
//        ResponseOrder returnValue = modelMapper.map(orderDto, ResponseOrder.class);

        /* kafka */
        orderDto.setOrderId(UUID.randomUUID().toString());
        orderDto.setTotalPrice(order.getUnitPrice() * order.getQty());
        ResponseOrder returnValue = modelMapper.map(orderDto, ResponseOrder.class);

        kafkaProducer.send("example-catalog-topic",orderDto);
        orderProducer.send("orders",orderDto);

        log.info("After added orders data");
        return ResponseEntity.status(HttpStatus.CREATED).body(returnValue);


    }
    @GetMapping("/{userId}/orders")
    public ResponseEntity<List<ResponseOrder>> getOrdersByUserId(@PathVariable("userId") String userId) throws Exception{
        log.info("Before retrieve orders data");
        Iterable<OrderEntity> orderEntities = orderService.getOrdersByUserId(userId);
        List<ResponseOrder> result = new ArrayList<>();
        orderEntities.forEach(v -> result.add(new ModelMapper().map(v,ResponseOrder.class)));

        try{
            Thread.sleep(1000);
            throw new Exception("장애 발생");
        } catch (InterruptedException e) {
            log.warn(e.getMessage());
        }

        log.info("After retrieved orders data");
        return ResponseEntity.status(HttpStatus.OK).body(result);
    }
}
