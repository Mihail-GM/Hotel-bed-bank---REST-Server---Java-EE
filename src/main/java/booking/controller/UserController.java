package booking.controller;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.google.gson.Gson;

import booking.model.Authority;
import booking.model.Booking;
import booking.model.CustomUserDetail;
import booking.model.CustomUserDetailsService;

import booking.model.User;
import booking.repository.AuthorityRepository;
import booking.repository.BookingRepository;
import booking.repository.UserRepository;
import booking.security.AllowedForAdmin;
import booking.security.SecurityConfig;
import booking.util.HotelNotFoundException;
import booking.util.InvalidTransactionDatabaseOrRestException;
import booking.util.UserNotFoundException;

@Service
@Controller
@RequestMapping(value = "/users")
@Transactional
public class UserController {

	@Autowired
	UserRepository users;

	@Autowired
	BookingRepository bookings;

	@Autowired
	AuthorityRepository authorities;

	@Autowired
	private AuthenticationManager authMgr;

	@Autowired
	private CustomUserDetailsService customUserDetailsSvc;

	// GET /users - the list of users
	@RequestMapping(method = RequestMethod.GET)
	@AllowedForAdmin
	public String index(Model model) {
		model.addAttribute("users", users.findAll());
		return "users/index";
	}

	// GET /users/new - the form to fill the data for a new user
	@RequestMapping(value = "/new", method = RequestMethod.GET)
	public String newHotel(Model model) {
		model.addAttribute("user", new User());

		return "users/create";
	}

	// REST add user
	@Transactional
	private void addUserToRest(User user, String urlOperation) throws Exception {
		Map<String, Object> paramsRole = new LinkedHashMap<>();
		paramsRole.put("name", user.getAuthority().getRole());

		Map<String, Object> params = new LinkedHashMap<>();
		params.put("role", paramsRole);
		params.put("id", Long.toString(user.getId()));
		params.put("username", user.getUsername());
		params.put("password", user.getPassword());
		params.put("email", user.getEmail());
		params.put("firstName", user.getFirstname());
		params.put("lasttName", user.getLastname());
		params.put("mobileNumber", user.getMobile());

		String json = new Gson().toJson(params, Map.class);

		URL url = new URL(urlOperation + "/faild");
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

		in.close();
		connection.disconnect();

	}

	// POST /users - creates a new user

	@RequestMapping(method = RequestMethod.POST)
	@Transactional(rollbackFor = InvalidTransactionDatabaseOrRestException.class)
	public String saveIt(@ModelAttribute User user, Model model) throws InvalidTransactionDatabaseOrRestException {

		try {
			// transaction if all success comit else roll back

			Authority authority = authorities.findByRole("ROLE_USER");
			user.setAuthority(authority);
			String pass = user.getPassword();
			user.setPassword(SecurityConfig.encoder.encode(user.getPassword()));
			users.save(user);
			UserDetails userDetails = customUserDetailsSvc.loadUserByUsername(user.getUsername());

			UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userDetails, pass,
					userDetails.getAuthorities());
			authMgr.authenticate(auth);
			SecurityContextHolder.getContext().setAuthentication(auth);

			// sessionObj.save(users);
			addUserToRest(user, "http://localhost:8081/api/user/add");

		} catch (Exception e) {
			throw new InvalidTransactionDatabaseOrRestException("\nError while calling Crunchify REST Service or DB");

		}
		return "redirect:/users/me";
	}

	// GET /login
	@RequestMapping(value = "/login", method = RequestMethod.GET)
	public String login(Model model) {
		return "index";
	}

	// GET /users/{id} - the user with identifier {id}
	@Transactional(rollbackFor = Exception.class)
	@RequestMapping(value = "{id}", method = RequestMethod.GET)
	@AllowedForAdmin
	public String show(@PathVariable("id") long id, Model model) {
		User user = users.findOne(id);
		if (user == null)
			throw new HotelNotFoundException();
		model.addAttribute("user", user);
		model.addAttribute("bookings", getUserBookings(user.getId()));
		return "users/show";
	}

	@RequestMapping(value = "/me", method = RequestMethod.GET)
	public String showActiveProfile(Model model) throws HotelNotFoundException {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		CustomUserDetail myUser = (CustomUserDetail) auth.getPrincipal();
		User user = users.findOne(myUser.getUser().getId());
		model.addAttribute("bookings", getUserBookings(user.getId()));
		model.addAttribute("user", user);
		return "users/show";
	}

	public Iterable<Booking> getUserBookings(long user_id) {
		Iterator<Booking> itbookings = bookings.findAll().iterator();
		List<Booking> bookingsList = new ArrayList<Booking>();

		while (itbookings.hasNext()) {
			Booking b = itbookings.next();
			if (b.getUser().getId() == user_id)
				bookingsList.add(b);
		}

		return bookingsList;
	}

	// GET /users/{id}/remove - removes the user with identifier {id}
	@Transactional(rollbackFor = Exception.class)
	@RequestMapping(value = "{id}/remove", method = RequestMethod.GET)
	@AllowedForAdmin
	public String remove(@PathVariable("id") long id, Model model) {
		try {
			User user = users.findOne(id);
			if (user == null)
				throw new UserNotFoundException();

			// Remove from rest

			addUserToRest(user, "http://localhost:8081/api/user/remove/" + Long.toString(id));

			users.delete(user);
			model.addAttribute("users", users.findAll());

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return "users/index";
	}

	// GET /users/{id}/edit - form to edit user
	@RequestMapping(value = "{id}/edit", method = RequestMethod.GET)
	public String edit(@PathVariable("id") long id, Model model) {
		User user = users.findOne(id);
		model.addAttribute("user", user);
		model.addAttribute("authorities", authorities.findAll());
		return "users/edit";
	}

	// POST /users/{id} - edit a user
	@Transactional(rollbackFor = InvalidTransactionDatabaseOrRestException.class)
	@RequestMapping(value = "/{id}", method = RequestMethod.POST)
	public String edit(@PathVariable("id") long id, @ModelAttribute User user, Model model)
			throws InvalidTransactionDatabaseOrRestException {
		try {
			users.save(user);
			addUserToRest(user, "http://localhost:8081/api/user/edit/" + Long.toString(user.getId()));
		} catch (Exception e) {
			throw new InvalidTransactionDatabaseOrRestException("\nError while calling Crunchify REST Service or DB");

		}

		model.addAttribute("user", user);
		return "redirect:/admin";
	}

}
