package com.hotel.booking.service;

import com.hotel.booking.model.BookingRequest;
import com.hotel.booking.repository.BookingDAO;
import com.hotel.booking.util.CurrencyConverter;

import java.time.temporal.ChronoUnit;

public class BookingService {

	private final PaymentService paymentService;
	private final RoomService roomService;
	private final BookingDAO bookingDAO;
	private final MailSenderService mailSenderService;

	private final static double BASE_PRICE_USD = 50.0;

	public int getAvailablePlaceCount() {
		return roomService.getAvailableRooms()
				.stream()
				.map(room -> room.getCapacity())
				.reduce(0, Integer::sum);
	}
	
	public double calculatePrice(BookingRequest bookingRequest) {
		long nights = ChronoUnit.DAYS.between(bookingRequest.getDateFrom(), bookingRequest.getDateTo());
		return BASE_PRICE_USD * bookingRequest.getGuestCount() * nights;
	}
	
	public double calculatePriceEuro(BookingRequest bookingRequest) {
		return CurrencyConverter.toEuro(calculatePrice(bookingRequest));
	}

	public String makeBooking(BookingRequest bookingRequest) {
		String roomId = roomService.findAvailableRoomId(bookingRequest);
		double price = calculatePrice(bookingRequest);

		if (bookingRequest.isPrepaid()) {
			paymentService.pay(bookingRequest, price);
		}

		bookingRequest.setRoomId(roomId);
		String bookingId = bookingDAO.save(bookingRequest);
		roomService.bookRoom(roomId);
		mailSenderService.sendBookingConfirmation(bookingId);
		return bookingId;
	}
	
	public void cancelBooking(String id) {
		BookingRequest request = bookingDAO.get(id);
		roomService.unbookRoom(request.getRoomId());
		bookingDAO.delete(id);
	}

	public BookingService(PaymentService paymentService, RoomService roomService, BookingDAO bookingDAO,
			MailSenderService mailSenderService) {
		super();
		this.paymentService = paymentService;
		this.roomService = roomService;
		this.bookingDAO = bookingDAO;
		this.mailSenderService = mailSenderService;
	}

}
