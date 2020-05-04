package booking.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

import booking.model.User;


@Transactional
public interface UserRepository extends CrudRepository<User, Long> {
	User findByUsername(String username);
}

