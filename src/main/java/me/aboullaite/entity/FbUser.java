package me.aboullaite.entity;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

@Entity
public class FbUser {

	@Id
	private String userId;
			
	private int reminder;
	
	public String getUserId() {
		return userId;
	}
	
	public void setUserId(String userId) {
		this.userId = userId;
	}
	
	public int getReminder() {
		return reminder;
	}
	
	public void setReminder(int reminder) {
		this.reminder = reminder;
	}
	
	@Override
	public String toString() {
		return "User [userId=" + userId + ", reminder=" + reminder + "]";
	}
	
}