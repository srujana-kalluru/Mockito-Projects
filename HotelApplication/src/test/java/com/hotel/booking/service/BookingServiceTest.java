package com.hotel.booking.service;

import com.hotel.booking.exception.BusinessException;
import com.hotel.booking.model.BookingRequest;
import com.hotel.booking.model.Room;
import com.hotel.booking.repository.BookingDAO;
import com.hotel.booking.util.CurrencyConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @InjectMocks
    private BookingService bookingService;
    @Mock
    MailSenderService mailSenderServiceMock;
    @Mock
    private RoomService roomServiceMock;
    @Mock
    private PaymentService paymentServiceMock;
    @Captor
    private ArgumentCaptor<Double> doubleCaptor;
    @Spy
    private BookingDAO bookingDao;

    BookingRequest.BookingRequestBuilder bookingRequestBuilder = BookingRequest.builder()
            .userId("1")
            .dateFrom(LocalDate.of(2020, 01, 01))
            .dateTo(LocalDate.of(2020, 01, 05))
            .guestCount(2);
    BookingRequest bookingRequestPrepaidFalse = bookingRequestBuilder
            .prepaid(false)
            .build();
    BookingRequest bookingRequestPrepaidTrue = bookingRequestBuilder
            .prepaid(true)
            .build();


    //Mocking static classes
    @Test
    void calculatePriceEuro_should_CalculateCorrectPrice() {

        try (MockedStatic<CurrencyConverter> mockedConverter = mockStatic(
                CurrencyConverter.class)) {
            double expected = 400.0 * 0.8;
            mockedConverter.when(() -> CurrencyConverter.toEuro(anyDouble()))
                    .thenAnswer(inv -> (double) inv.getArgument(0) * 0.8);

            // when
            double actual = bookingService.calculatePriceEuro(bookingRequestPrepaidFalse);

            // then
            assertEquals(expected, actual);
        }
    }

    //Simple assert equals
    @Test
    void calculatePrice_should_CalculateCorrectPrice_When_CorrectInput() {
        double expected = 4 * 2 * 50.0;

        // when
        double actual = bookingService.calculatePrice(bookingRequestPrepaidFalse);

        // then
        assertEquals(expected, actual);
    }

    //do return
    @Test
    void cancelBooking_should_CancelBooking_When_InputOK() {
        bookingRequestPrepaidTrue.setRoomId("1.3");
        String bookingId = "1";

        doReturn(bookingRequestPrepaidTrue).when(bookingDao).get(bookingId);

        Executable executable = () -> bookingService.cancelBooking(bookingId);

        // then
        // no exception thrown
        assertDoesNotThrow(executable);
    }

    //Throw an exception
    @Test
    void findAvailableRoomId_should_ThrowException_When_NoRoomAvailable() {
        // given
        Mockito.when(roomServiceMock.findAvailableRoomId(bookingRequestPrepaidFalse))
                .thenThrow(BusinessException.class);

        // when
        Executable executable = () -> bookingService.makeBooking(bookingRequestPrepaidFalse);

        // then
        assertThrows(BusinessException.class, executable);

    }

    @Test
    void getAvailablePlaceCount_should_CountAvailablePlaces() {
        // given
        int expected = 0;

        // when
        int actual = bookingService.getAvailablePlaceCount();

        // then
        assertEquals(expected, actual);
    }

    @Test
    void getAvailablePlaceCount_should_CountAvailablePlaces_When_CalledMultipleTimes() {
        // given
        when(roomServiceMock.getAvailableRooms())
                .thenReturn(Collections.singletonList(new Room("Room 1", 5)))
                .thenReturn(Collections.emptyList());
        int expectedFirstCall = 5;
        int expectedSecondCall = 0;

        // when
        int actualFirst = bookingService.getAvailablePlaceCount();
        int actualSecond = bookingService.getAvailablePlaceCount();

        // then
        assertAll(() -> assertEquals(expectedFirstCall, actualFirst),
                () -> assertEquals(expectedSecondCall, actualSecond));
    }

    @Test
    void getAvailablePlaceCount_should_CountAvailablePlaces_When_MultipleRoomsAvailable() {
        // given
        List<Room> rooms = Arrays.asList(new Room("Room 1", 2), new Room("Room 2", 5));
        when(roomServiceMock.getAvailableRooms()).thenReturn(rooms);
        int expected = 7;

        // when
        int actual = bookingService.getAvailablePlaceCount();

        // then
        assertEquals(expected, actual);
    }

    @Test
    void getAvailablePlaceCount_should_CountAvailablePlaces_When_OneRoomAvailable() {
        // given
        when(roomServiceMock.getAvailableRooms())
                .thenReturn(Collections.singletonList(new Room("Room 1", 5)));
        int expected = 5;

        // when
        int actual = bookingService.getAvailablePlaceCount();

        // then
        assertEquals(expected, actual);
    }

    @Test
    void makeBooking_should_InvokePayment_When_Prepaid() {
        // when
        Executable executable = () -> bookingService.makeBooking(bookingRequestPrepaidTrue);
        assertDoesNotThrow(executable);

        // then
        verify(paymentServiceMock, times(1)).pay(bookingRequestPrepaidTrue, 400.0);
        verifyNoMoreInteractions(paymentServiceMock);
    }

    @Test
    void makeBooking_should_NotCompleteBooking_When_PriceTooHigh() {
        // given

        when(paymentServiceMock.pay(any(), anyDouble())).thenThrow(BusinessException.class);

        // when
        Executable executable = () -> bookingService.makeBooking(bookingRequestPrepaidTrue);

        // then
        assertThrows(BusinessException.class, executable);
    }

    @Test
    void makeBooking_should_NotThrowException_When_MailNotReady() {
        doNothing().when(mailSenderServiceMock).sendBookingConfirmation(any());

        // when
        Executable executable = () -> bookingService.makeBooking(bookingRequestPrepaidFalse);

        // then
        // no exception thrown
        assertDoesNotThrow(executable);
    }

    @Test
    void makeBooking_should_PayCorrectPrice_When_InputOK() {
        // when
        bookingService.makeBooking(bookingRequestPrepaidTrue);

        // then
        verify(paymentServiceMock, times(1)).pay(eq(bookingRequestPrepaidTrue), doubleCaptor.capture());
        double capturedArgument = doubleCaptor.getValue();
        assertEquals(400.0, capturedArgument);
    }

    @Test
    void makeBooking_should_PayCorrectPrices_When_MultipleCalls() {
        // given
        BookingRequest bookingRequest = BookingRequest.builder()
                .userId("1")
                .dateFrom(LocalDate.of(2020, 01, 01))
                .dateTo(LocalDate.of(2020, 01, 02))
                .guestCount(2)
                .prepaid(true)
                .build();
        List<Double> expectedValues = Arrays.asList(400.0, 100.0);

        // when
        bookingService.makeBooking(bookingRequestPrepaidTrue);
        bookingService.makeBooking(bookingRequest);

        // then
        verify(paymentServiceMock, times(2)).pay(any(), doubleCaptor.capture());
        List<Double> capturedArguments = doubleCaptor.getAllValues();

        assertEquals(expectedValues, capturedArguments);
    }

    @Test
    void makeBooking_should_NotInvokePayment_When_NotPrepaid() {

        // when
        bookingService.makeBooking(bookingRequestPrepaidFalse);

        // then
        verify(paymentServiceMock, never()).pay(any(), anyDouble());
    }

    @Test
    void makeBooking_should_ThrowException_When_MailNotReady() {

        doThrow(new BusinessException()).when(mailSenderServiceMock).sendBookingConfirmation(any());

        // when
        Executable executable = () -> bookingService.makeBooking(bookingRequestPrepaidFalse);

        // then
        assertThrows(BusinessException.class, executable);
    }

    @Test
    void saveBooking_should_MakeBooking_When_InputOK() {

        // when
        String bookingId = bookingService.makeBooking(bookingRequestPrepaidTrue);

        // then
        verify(bookingDao, times(1)).save(bookingRequestPrepaidTrue);
    }

}
