package booking.controller;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.io.IOUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.SessionAttributes;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import booking.model.Booking;
import booking.model.Hotel;
import booking.model.Room;
import booking.model.RoomType;
import booking.repository.BookingRepository;
import booking.repository.HotelRepository;
import booking.repository.RoomRepository;

@Controller
@RequestMapping(value = "/")
@SessionAttributes({ "booking" })
public class RestReservationControler {
	@Autowired
	HotelRepository hotels;

	@Autowired
	BookingRepository bookings;

	@Autowired
	RoomRepository rooms;

	@RequestMapping(value = "/results/results", method = RequestMethod.POST)
	public String searchRoomsRest(@ModelAttribute("startDate") Date startDate, Model model,
			@ModelAttribute("endDate") Date endDate) {

		System.out.println("Booking start date" + startDate);

		model.addAttribute("hotels", getRoomAvaibelityFromRest(startDate, endDate));

		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
		String dateStart = formatter.format(startDate);
		String dateEnd = formatter.format(endDate);

		model.addAttribute("startDate", dateStart);
		model.addAttribute("endDate", dateEnd);

		return "results/results";
	}

	private HashSet getRoomAvaibelityFromRest(Date checkin, Date checkout) {
		HashSet<Hotel> setOfHotels = new HashSet();

		Map<String, Object> params = new LinkedHashMap<>();
		System.out.println("The data format" + checkin);

		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
		String dateStart = formatter.format(checkin);
		String dateEnd = formatter.format(checkout);

		// add parameters to JSON
		params.put("startDate", dateStart);
		params.put("endDate", dateEnd);
		params.put("lowestPrice", 1);
		params.put("highestPrice", 99999999);

		System.out.println("startDate : " + dateStart);
		System.out.println("endDate : " + dateEnd);

		String json = new Gson().toJson(params, Map.class);

		System.out.println("Json to REST : " + json);
		try {

			URL url = new URL("http://localhost:8081/api/findrooms");
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setDoOutput(true);
			connection.setRequestProperty("Content-Type", "application/json");
			connection.setConnectTimeout(5000);
			connection.setReadTimeout(5000);
			connection.setRequestMethod("POST");

			OutputStream os = connection.getOutputStream();
			os.write(json.getBytes("UTF-8"));
			os.close();

			InputStream in = new BufferedInputStream(connection.getInputStream());
			String results = IOUtils.toString(in, "UTF-8");

			Gson gson = new Gson();
			RoomDTO[] roomArray = gson.fromJson(results, RoomDTO[].class);

			for (RoomDTO room : roomArray) {

				Hotel hotel = hotels.findOne(rooms.findOne(room.getRoomdId()).getHotel().getId());

				setOfHotels.add(hotel);
			}

			in.close();
			connection.disconnect();

		} catch (Exception e) {
			System.out.println("\nError while calling Crunchify REST Service");
			System.out.println(e);
		}
		return setOfHotels;

	}

	class RoomDTO {
		private long roomId;
		private long pricePerDay;
		private String photoLink;
		private long rating;

		public RoomDTO() {

		}

		public RoomDTO(long roomId, long pricePerDay, String photoLink, long rating) {

			this.roomId = roomId;
			this.pricePerDay = pricePerDay;
			this.photoLink = photoLink;
			this.rating = rating;
		}

		public long getRoomdId() {
			return roomId;
		}

		public void setRoomdId(long roomdID) {
			this.roomId = roomdID;
		}

		public long getPricePerDay() {
			return pricePerDay;
		}

		public void setPricePerDay(long pricePerDay) {
			this.pricePerDay = pricePerDay;
		}

		public String getPhotoLink() {
			return photoLink;
		}

		public void setPhotoLink(String photoLink) {
			this.photoLink = photoLink;
		}

		public long getRating() {
			return rating;
		}

		public void setRating(long rating) {
			this.rating = rating;
		}

		@Override
		public String toString() {
			return "RoomDTO [roomdID=" + roomId + ", pricePerDay=" + pricePerDay + ", photoLink=" + photoLink
					+ ", rating=" + rating + "]";
		}

	}

}
