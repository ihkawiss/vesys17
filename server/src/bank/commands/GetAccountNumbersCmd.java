package bank.commands;

import java.io.Serializable;
import java.util.Set;

public class GetAccountNumbersCmd implements Serializable {

	private static final long serialVersionUID = -3160048595474696419L;

	private Set<String> accounts;

	public Set<String> getAccounts() {
		return accounts;
	}

	public void setAccounts(Set<String> accounts) {
		this.accounts = accounts;
	}

}
