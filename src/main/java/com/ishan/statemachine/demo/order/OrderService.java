package com.ishan.statemachine.demo.order;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import lombok.extern.java.Log;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.statemachine.support.StateMachineInterceptorAdapter;
import org.springframework.statemachine.transition.Transition;
import org.springframework.stereotype.Service;
@Log

@Service
public class OrderService {

	private final OrderRepository orderRepository;
	private final StateMachineFactory<OrderStates, OrderEvents> factory;

	private static final String ORDER_ID_HEADER = "orderId";

	OrderService(OrderRepository orderRepository, StateMachineFactory<OrderStates, OrderEvents> factory) {
		this.orderRepository = orderRepository;
		this.factory = factory;
	}

	Order byId(Long id) {
		log.info("tttxxx id " + id);
		return this.orderRepository.findById(id).get();
	}

	public void feedOrders() {
//		this.orderRepository.save(new Order(new Date(), OrderStates.SUBMITTED));
//		this.orderRepository.save(new Order(new Date(), OrderStates.SUBMITTED));
//		this.orderRepository.save(new Order(new Date(), OrderStates.SUBMITTED));
	}

	Order create(Date when) {
//		return  this.orderRepository.save(new Order(Instant.parse(when.toString(), OrderStates.SUBMITTED));
		return  this.orderRepository.save(createModel(when, OrderStates.SUBMITTED));
	}
	Order createModel(Date orderDate, OrderStates state) {
		Order order = new Order();
		order.setOrderDate(orderDate.toInstant());
		order.setOrderState(state);

		return order;
	}
	StateMachine<OrderStates, OrderEvents> pay(Long orderId, String paymentConfirmationNumber) {
		StateMachine<OrderStates, OrderEvents> sm = this.build(orderId);

		Message<OrderEvents> paymentMessage = MessageBuilder.withPayload(OrderEvents.PAY)
				.setHeader(ORDER_ID_HEADER, orderId)
				.setHeader("paymentConfirmationNumber", paymentConfirmationNumber)
				.build();

		sm.sendEvent(paymentMessage);
		
		return sm;
	}

	StateMachine<OrderStates, OrderEvents> fulfill(Long orderId) {
		StateMachine<OrderStates, OrderEvents> sm = this.build(orderId);
		Message<OrderEvents> fulfillmentMessage = MessageBuilder.withPayload(OrderEvents.FULFILL)
				.setHeader(ORDER_ID_HEADER, orderId)
				.build();
		sm.sendEvent(fulfillmentMessage);
		return sm;
	}

	private StateMachine<OrderStates, OrderEvents> build(Long orderId) {
		Order order = this.orderRepository.findById(orderId).get();
		String orderIdKey = Long.toString(order.getId());

		StateMachine<OrderStates, OrderEvents> sm = this.factory.getStateMachine(orderIdKey);
		sm.stop();
		sm.getStateMachineAccessor()
				.doWithAllRegions(sma -> {

					sma.addStateMachineInterceptor(new StateMachineInterceptorAdapter<OrderStates, OrderEvents>() {

						@Override
						public void preStateChange(State<OrderStates, OrderEvents> state, Message<OrderEvents> message, Transition<OrderStates, OrderEvents> transition, StateMachine<OrderStates, OrderEvents> stateMachine) {

							Optional.ofNullable(message).ifPresent(msg -> {
								log.info("ttt 000 msg " +  msg);

								Optional.ofNullable(Long.class.cast(msg.getHeaders().getOrDefault(ORDER_ID_HEADER, -1L)))
										.ifPresent(orderId -> {
											log.info("ttt 111 orderId " +  orderId);
											Order order = orderRepository.findById(orderId).get();
											order.setOrderState(state.getId());
											log.info("ttt 222 order  " +  order);
											orderRepository.save(order);
										});
							});

						}
					});
					sma.resetStateMachine(new DefaultStateMachineContext<>(order.getOrderState(), null, null, null));
				});
		sm.start();
		return sm;
	}
}