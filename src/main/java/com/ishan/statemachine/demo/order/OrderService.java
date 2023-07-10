package com.ishan.statemachine.demo.order;

import java.util.Date;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineBuilder;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.statemachine.support.StateMachineInterceptorAdapter;
import org.springframework.statemachine.transition.Transition;
import org.springframework.stereotype.Service;

@Service
public class OrderService {
	private Logger logger = LoggerFactory.getLogger(this.getClass());
	private final OrderRepository orderRepository;

	private static final String ORDER_ID_HEADER = "orderId";

	OrderService(OrderRepository orderRepository) {
		this.orderRepository = orderRepository;
	}

	Order byId(Long id) {
		logger.info("id {} ", id);
		return this.orderRepository.findById(id).get();
	}

	Order create(Date when) {
		return this.orderRepository.save(createModel(when, OrderStates.SUBMITTED));
	}

	Order createModel(Date orderDate, OrderStates state) {
		Order order = new Order();
		order.setOrderDate(orderDate.toInstant());
		order.setOrderState(state);

		return order;
	}

	StateMachine<OrderStates, OrderEvents> pay(Long orderId, String paymentConfirmationNumber) {
		StateMachine<OrderStates, OrderEvents> sm = this.createStateMachine(orderId);

		Message<OrderEvents> paymentMessage = MessageBuilder.withPayload(OrderEvents.PAY)
				.setHeader(ORDER_ID_HEADER, orderId)
				.setHeader("paymentConfirmationNumber", paymentConfirmationNumber)
				.build();

		sm.sendEvent(paymentMessage);

		return sm;
	}

	StateMachine<OrderStates, OrderEvents> fulfillOrder(Long orderId) {
		StateMachine<OrderStates, OrderEvents> sm = this.createStateMachine(orderId);
		Message<OrderEvents> fulfillmentMessage = MessageBuilder.withPayload(OrderEvents.FULFILL)
				.setHeader(ORDER_ID_HEADER, orderId)
				.build();
		sm.sendEvent(fulfillmentMessage);
		return sm;
	}
	StateMachine<OrderStates, OrderEvents> cancelOrder(Long orderId) {
		logger.info("cancelOrder orderId {} ", orderId);
		StateMachine<OrderStates, OrderEvents> sm = this.createStateMachine(orderId);
		logger.info("sm {} ", sm);
		Message<OrderEvents> cancelOrderMessage = MessageBuilder.withPayload(OrderEvents.CANCEL)
				.setHeader(ORDER_ID_HEADER, orderId)
				.build();
		logger.info("cancelOrderMessage {} ", cancelOrderMessage);
		sm.sendEvent(cancelOrderMessage);
		return sm;
	}

	private StateMachine<OrderStates, OrderEvents> configureStateMachine() {
		try {
			StateMachineBuilder.Builder<OrderStates, OrderEvents> builder = StateMachineBuilder.builder();
			builder.configureStates().withStates()
					.initial(OrderStates.SUBMITTED)
					.state(OrderStates.PAID)
					.end(OrderStates.FULFILLED)
					.end(OrderStates.CANCELLED);

			builder.configureTransitions()
					.withExternal().source(OrderStates.SUBMITTED).target(OrderStates.PAID)
					.event(OrderEvents.PAY)
					.and()
					.withExternal().source(OrderStates.PAID).target(OrderStates.FULFILLED).event(OrderEvents.FULFILL)
					.and()
					.withExternal().source(OrderStates.SUBMITTED).target(OrderStates.CANCELLED)
					.event(OrderEvents.CANCEL)
					.and()
					.withExternal().source(OrderStates.PAID).target(OrderStates.CANCELLED).event(OrderEvents.CANCEL);
			StateMachine<OrderStates, OrderEvents> machine = builder.build();

			return  machine;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	private StateMachine<OrderStates, OrderEvents> createStateMachine(Long orderId) {
		Order order = this.orderRepository.findById(orderId).get();
logger.info("order {} ", order);
		try {
			StateMachine<OrderStates, OrderEvents> machine = configureStateMachine();
			machine.stop();
			machine.getStateMachineAccessor()
					.doWithAllRegions(sma -> {
						sma.addStateMachineInterceptor(new StateMachineInterceptorAdapter<OrderStates, OrderEvents>() {

							@Override
							public void preStateChange(State<OrderStates, OrderEvents> state,
									Message<OrderEvents> message, Transition<OrderStates, OrderEvents> transition,
									StateMachine<OrderStates, OrderEvents> stateMachine) {

								Optional.ofNullable(message).ifPresent(msg -> {
									logger.info("msg {} ", msg);

									Optional.ofNullable(
											Long.class.cast(msg.getHeaders().getOrDefault(ORDER_ID_HEADER, -1L)))
											.ifPresent(orderId -> {
												logger.info("msg {} ", msg);
												Order order = orderRepository.findById(orderId).get();
												order.setOrderState(state.getId());
												orderRepository.save(order);
											});
								});

							}
						});
						sma.resetStateMachine(
								new DefaultStateMachineContext<>(order.getOrderState(), null, null, null));
					});
			machine.start();
			logger.info("machine {} ", machine);

			return machine;

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}