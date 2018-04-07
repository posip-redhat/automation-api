package com.rhc.automation.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * Home redirection to swagger api documentation 
 */
@Controller
public class HomeController {
	
    private final String indexPage = "redirect:swagger-ui.html";
	
	/**
	@RequestMapping(value = "/")
	public String index() {
		System.out.println("swagger-ui.html");
		return "redirect:swagger-ui.html";
	}
	**/
	
    @RequestMapping(value = "/", method = RequestMethod.GET)
    public String readData() {
        // No state-changing operations performed within this method.
        return indexPage;
    }	
}
