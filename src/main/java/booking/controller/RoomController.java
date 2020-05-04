package booking.controller;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.google.gson.Gson;

import booking.model.Booking;
import booking.model.Hotel;
import booking.model.Room;
import booking.repository.BookingRepository;
import booking.repository.HotelRepository;
import booking.repository.RoomRepository;
import booking.repository.RoomTypeRepository;
import booking.security.AllowedForAdmin;
import booking.util.InvalidTransactionDatabaseOrRestException;

@Controller
@RequestMapping(value = "/hotels")
public class RoomController {

	@Autowired
	HotelRepository hotels;

	@Autowired
	RoomTypeRepository roomTypes;

	@Autowired
	RoomRepository rooms;

	@Autowired
	BookingRepository bookings;

	// GET /hotels/{id}/rooms/new - the form to fill the data for a new room
	@RequestMapping(value = "{id}/rooms/new", method = RequestMethod.GET)
	@AllowedForAdmin
	public String newRoom(@PathVariable("id") long id, Model model) {
		Room r = new Room();
		model.addAttribute("hotel", hotels.findOne(id));
		model.addAttribute("room", r);
		model.addAttribute("roomTypes", roomTypes.findAll());
		return "rooms/create";
	}

	// POST /hotels/{id}/rooms/ - creates a new room
	@RequestMapping(value = "{id}/rooms", method = RequestMethod.POST)
	@AllowedForAdmin
	@Transactional(rollbackFor = InvalidTransactionDatabaseOrRestException.class)
	public String saveRoom(@PathVariable("id") long id, @ModelAttribute Room room, Model model)
			throws InvalidTransactionDatabaseOrRestException {
		try {
			Hotel hotel = hotels.findOne(id);
			room.setHotel(hotel);
			rooms.save(room);

			roomToRest(hotel, room, "http://localhost:8081/api/room/add", "POST");
		} catch (Exception e) {
			throw new InvalidTransactionDatabaseOrRestException("\nError while calling Crunchify REST Service or DB");

		}
		return "redirect:/hotels/" + id + "/rooms";
	}

	// add room to REST server
	private void roomToRest(Hotel hotel, Room room, String urlOperation, String requestMethod) throws Exception {
		Map<String, Object> paramsRole = new LinkedHashMap<>();
		paramsRole.put("id", room.getHotel().getId());

		Map<String, Object> params = new LinkedHashMap<>();

		params.put("id", room.getId());
		params.put("roomNumber", room.getRoom_number());
		params.put("pricePerDay", Integer.toString(room.getPrice()));
		params.put("capacity", "0");
		params.put("petFriendly", room.getPetFriendly());
		params.put("description", room.getDescription());
		params.put("photoLink", "NULL");
		params.put("rating", Integer.toString(hotel.getRating()));
		params.put("hotel", paramsRole);

		String json = new Gson().toJson(params, Map.class);

		URL url = new URL(urlOperation);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setDoOutput(true);
		connection.setRequestProperty("Content-Type", "application/json");
		connection.setConnectTimeout(5000);
		connection.setReadTimeout(5000);
		connection.setRequestMethod(requestMethod);

		OutputStream os = connection.getOutputStream();
		os.write(json.getBytes("UTF-8"));
		os.close();

		InputStream in = new BufferedInputStream(connection.getInputStream());
		String results = IOUtils.toString(in, "UTF-8");

		in.close();
		connection.disconnect();

	}

	// GET /hotels/{id}/rooms/ - show the list of rooms of the hotel
	@RequestMapping(value = "{id}/rooms", method = RequestMethod.GET)
	@AllowedForAdmin
	public String showRooms(@PathVariable("id") long id, Model model) {
		Hotel hotel = hotels.findOne(id);
		Map<Long, Room> hotel_rooms = hotel.getRooms();
		Map<Integer, Room> rooms = new HashMap<Integer, Room>();

		for (Long entry : hotel_rooms.keySet()) {
			Room r = hotel_rooms.get(entry);
			rooms.put(Integer.parseInt(r.getRoom_number()), r);
		}
		List<Room> orderedRooms = new ArrayList<Room>();
		SortedSet<Integer> orderedSet = new TreeSet<Integer>(rooms.keySet());
		for (Integer key : orderedSet)
			orderedRooms.add(rooms.get(key));

		model.addAttribute("hotel", hotel);
		model.addAttribute("orderedRooms", orderedRooms);
		return "rooms/hotel-rooms";
	}

	// GET /hotels/{id}/rooms/{id_room}/edit - shows the form to edit a room
	@RequestMapping(value = "{id}/rooms/{id_room}/edit", method = RequestMethod.GET)
	@AllowedForAdmin
	public String editRoom(@PathVariable("id") long id, @PathVariable("id_room") long id_room, Model model) {
		Hotel hotel = hotels.findOne(id);
		model.addAttribute("hotel", hotel);
		model.addAttribute("room", hotel.getRooms().get(id_room));
		model.addAttribute("roomTypes", roomTypes.findAll());
		return "rooms/edit";
	}

	@RequestMapping(value = "{id}/rooms/{id_room}/remove", method = RequestMethod.GET)
	@AllowedForAdmin
	@Transactional(rollbackFor = InvalidTransactionDatabaseOrRestException.class)
	public String removeRoom(@PathVariable("id") long id, @PathVariable("id_room") long id_room, Model model)
			throws InvalidTransactionDatabaseOrRestException {
		try {
			Hotel hotel = hotels.findOne(id);

			for (Booking b : rooms.findOne(id_room).getBookings()) {
				bookings.delete(b);
			}

			roomToRest(hotel, rooms.findOne(id_room), "http://localhost:8081/api/room/" + id_room, "DELETE");
			rooms.delete(id_room);

			model.addAttribute("hotel", hotel);
		} catch (Exception e) {
			throw new InvalidTransactionDatabaseOrRestException("\nError while calling Crunchify REST Service or DB");

		}

		return "redirect:/hotels/{id}/rooms";
	}
}
