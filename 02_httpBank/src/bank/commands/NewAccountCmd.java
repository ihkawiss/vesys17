package bank.commands;

import java.io.Serializable;

public class NewAccountCmd implements Serializable {

	private static final long serialVersionUID = 3780763785304503670L;

	private String owner;
	private String accountNumber;

	public NewAccountCmd(String owner) {
		this.owner = owner;
	}

	public String getOwner() {
		return owner;
	}

	public void setOwner(String owner) {
		this.owner = owner;
	}

	public String getAccountNumber() {
		return accountNumber;
	}

	public void setAccountNumber(String accountNumber) {
		this.accountNumber = accountNumber;
	}

}
