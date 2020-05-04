package booking.util;

import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(reason="Eror while trying to set data to database or to REST server") 
public class InvalidTransactionDatabaseOrRestException extends Exception {
	  /**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public InvalidTransactionDatabaseOrRestException(String message) {
	      super(message);
	  }
}