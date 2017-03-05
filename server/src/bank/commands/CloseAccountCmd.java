package bank.commands;

import java.io.Serializable;

public class CloseAccountCmd implements Serializable {

	private static final long serialVersionUID = 5087993916391699909L;

	private String accountNr;
	private boolean result;

	public CloseAccountCmd(String number) {
		this.accountNr = number;
	}

	public String getAccountNr() {
		return accountNr;
	}

	public void setAccountNr(String accountNr) {
		this.accountNr = accountNr;
	}

	public boolean wasClosed() {
		return result;
	}

	public void setResult(boolean result) {
		this.result = result;
	}

}
