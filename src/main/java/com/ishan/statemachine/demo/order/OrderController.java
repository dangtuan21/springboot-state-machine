package com.ishan.statemachine.demo.order;

import java.util.Date;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.statemachine.StateMachine;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.inject.Inject;

@RestController
@RequestMapping("/order")
public class OrderController {
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	// @Autowired
	@Inject
	private OrderService orderService;

	@GetMapping("/{id}")
	public Order getOrder(@PathVariable("id") Long id) {
		Order order = orderService.byId(id);
		return order;
	}

	@PostMapping("/")
	public Order createOrder() {
		Order order = orderService.create(new Date());
		return order;
	}

	@PostMapping("/pay/{id}")
	public Order payOrder(@PathVariable("id") Long id) {
		Order order = orderService.byId(id);
		StateMachine<OrderStates, OrderEvents> paymentStateMachine = orderService.pay(order.getId(),
				UUID.randomUUID().toString());
		logger.info("after calling pay(): " + paymentStateMachine.getState().getId().name());
		logger.info("order: " + orderService.byId(order.getId()));
		return order;
	}

	@PostMapping("/fulfill/{id}")
	public Order fulfillOrder(@PathVariable("id") Long id) {
		Order order = orderService.byId(id);

		StateMachine<OrderStates, OrderEvents> fulfilledStateMachine = orderService.fulfillOrder(order.getId());
		logger.info("after calling fulfill(): " + fulfilledStateMachine.getState().getId().name());
		logger.info("order: " + orderService.byId(order.getId()));
		return order;
	}
	@PostMapping("/cancel/{id}")
	public Order cancelOrder(@PathVariable("id") Long id) {
		logger.info("cancelOrder id {} " , id);
		Order order = orderService.byId(id);
		logger.info("cancelOrder order {} " , order);

		StateMachine<OrderStates, OrderEvents> canceledStateMachine = orderService.cancelOrder(order.getId());
		logger.info("after calling cancelOrder(): " + canceledStateMachine.getState().getId().name());
		logger.info("order: " + orderService.byId(order.getId()));
		return order;
	}
}
