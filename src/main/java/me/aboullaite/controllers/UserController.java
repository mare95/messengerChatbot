package me.aboullaite.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

import java.util.List;

import javax.validation.Valid;

import me.aboullaite.data.UserRespository;
import me.aboullaite.entity.FbUser;

@Controller
public class UserController {

	@Autowired
	UserRespository userData;
	
	//Only for testing database localy
	@RequestMapping(value = "/addNewUser.html", method = RequestMethod.POST)
	public String newUser(@Valid FbUser user, BindingResult bindingResult) {
		
		userData.save(user);
		return ("redirect:/listUsers.html");

	}
	
	//Only for testing database localy
	@RequestMapping(value = "/addNewUser.html", method = RequestMethod.GET)
	public ModelAndView addNewUser() {

		return new ModelAndView("newUser", "form", new FbUser());

	}

	@RequestMapping(value = "/listUsers.html", method = RequestMethod.GET)
	public ModelAndView users() {
		
		List<FbUser> allUsers = userData.findAll();
		return new ModelAndView("allUsers", "users", allUsers);

	}

}
