package com.jsontest;

import org.json.*;

import java.util.Iterator;
import java.util.Vector;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Enumeration;
import javax.servlet.http.*;

@SuppressWarnings("serial")
public class JsontestcomServlet extends HttpServlet {
	public void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {
		doPost(req, resp);
		
	}
	
	public void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {
		
		/**
		 * We need to set the Access-Control-Allow-Origin header to *. Users can opt 
		 * out of receiving this header by setting the "alloworigin" parameter to false.
		 */
		String allow_origin = req.getParameter("alloworigin");
		if ("false".equals(allow_origin)) {
			//Do nothing. We may later expand this section to allow users to modify the header itself.
		}
		else {
			resp.setHeader("Access-Control-Allow-Origin", "*");
		}
		
		
		/**
		 * First, we grab the requesting url and peel out the subdomain used in 
		 * the request. Once we have the subdomain isolated, we can figure out 
		 * what service the user is requesting.
		 */
		String requesting_url = req.getRequestURL().toString();
		int subdomain_index = requesting_url.indexOf(".") + 1;
		requesting_url = requesting_url.substring(0, subdomain_index);
		
		/**
		 * Uncomment the below lines if you want to specify the service via a parameter.
		 */
		/**
		requesting_url = req.getParameter("service");
		if (requesting_url == null) {
			requesting_url = "echo";
		}
		**/
		
		
		
		//This stores the name:value pairs that go into the response JSON.
		//We use Object as the value because we want to be free to specify a 
		//String object, a number object (i.e. java.lang.Integer) or a Boolean value.
		Hashtable<String, Object> response_map = new Hashtable<String, Object>();
		
		/**
		 * Some functions require us to post malformed or non-json content.
		 * They can directly store their results into this string, and we'll 
		 * handle the callbacks and outputstream writing at the end.
		 */
		String response_json = null;
		
		//Now, figure out what service the user wants, and execute it.
		if (requesting_url.contains("validate")) {
			//Validate the incoming JSON
			
			try {				
				String incoming_json = req.getParameter("json");
				
				if (incoming_json == null) {
					throw new JSONException("No JSON to validate. Please post JSON to validate via the json parameter.");
				}
				
				JSONObject json_object = new JSONObject(incoming_json);
				
				/**
				 * If we've managed to get to this point, the JSONHandler was able 
				 * to parse the JSON string into a JSON object. Therefore, validate 
				 * should be true.
				 */
				response_map.put("validate", new Boolean(true));
				
				/**
				 * Now we figure out the size of the JSON object.
				 */
				int size = getFirstLevelKeys(json_object).size();
				response_map.put("size", new Integer(size));
				
				/**
				 * If the JSON object contains key:value pairs, note that it is not 
				 * empty. If the object does not contain any pairs, note that it is empty.
				 */
				if (size > 0) {
					response_map.put("empty", new Boolean(false));
				}
				else {
					response_map.put("empty", new Boolean(true));
				}
			}
			catch (JSONException e) {
				/**
				 * A JSONException occurred, which means that the JSON string 
				 * had something wrong with it. Note the error, and note that validation 
				 * failed.
				 */
				response_map.put("validate", new Boolean(false));
				response_map.put("error", e.getMessage());
			}	
			
		}//end else if validate
		else if (requesting_url.contains("date") || requesting_url.contains("time")) {
			//Send back date and time
			//@TODO implement time zone functionality
			String date = new java.text.SimpleDateFormat("MM-dd-yyyy").format(new java.util.Date());
			String time = new java.text.SimpleDateFormat("hh:mm:ss aa").format(new java.util.Date());
			response_map.put("date", date);
			response_map.put("time", time);
		}
		else if (requesting_url.contains("ip")) {
			//Send back IP address
			String ip = req.getRemoteAddr();
			response_map.put("ip", ip);
		}
		else if (requesting_url.contains("header")) {
			//Send back headers
			Enumeration<String> headers = req.getHeaderNames();
			while (headers.hasMoreElements()) {
				String header = headers.nextElement();
				
				/**
				 * We host on App Engine, and GAE includes some special headers that 
				 * start with X-AppEngine. For security purposes, we're not going to return 
				 * those.
				 */
				if ((header.toLowerCase()).startsWith("x-appengine")) {
					break;
				}
				
				String value = req.getHeader(header);
				response_map.put(header, value);
			}
		}
		else if (requesting_url.contains("code")) {
			/**
			 * The user wants an example of arbitrary JS code.
			 */
			resp.setContentType("text/plain");
			
			String code = "alert(\"IP Address: " + req.getRemoteAddr() + "\"); \r\n";
			code += "alert(\"Browser: " + req.getHeader("User-Agent") + "\"); \r\n";
			
			String callback = req.getParameter("callback");
			
			if (callback != null) {
				code += callback + "(" + generateGenericJSON() + "); \r\n";
			}
			
			resp.getWriter().print(code);
			return;
		}
		else if (requesting_url.contains("echo")) {
			String request_uri = req.getRequestURI().substring(1);
			String[] components = request_uri.split("/");
			
			for (int i = 0; i < components.length; i++) {
				String key = components[i];
				String value = "";
				try {
					value = components[i + 1];
				}
				catch (ArrayIndexOutOfBoundsException e) {
					//If this exception is thrown, that means there are an odd number of tokens
					//in the request url (in other terms, there is a key value specified, but no 
					//value). It's OK, because we'll just put a blank string into the value component.
				}
				response_map.put(key, value);
				i++;
			}
			
		}
		else if (requesting_url.startsWith("malform")) {
			//The user wants malformed JSON
			
			response_json = generateGenericJSON();
			response_json = response_json.trim();
			response_json = response_json.substring(0, response_json.length() - 1);
			
		}
		else if (requesting_url.contains("cookie")) {
			//The user wants us to set a cookie.
			javax.servlet.http.Cookie cookie = new javax.servlet.http.Cookie("jsontestdotcom", "jsontestcomsamplecookie");
			cookie.setMaxAge(60 * 60 * 24 * 14); //Two weeks to expire.
			resp.addCookie(cookie);
			
			response_map.put("cookie", "Cookie is set with name jsontestdotcom and value jsontestcomsamplecookie");
		}
		else {
			//If the request doesn't match anything above, we're just going to send back 
			//some information in JSON form.
			String error_explain = "A valid JSONTest service was not specified.";
			String info_explain = "Please visit www.jsontest.com for more information.";
			String version_explain = "0.1 beta";
			response_map.put("error", error_explain);
			response_map.put("info", info_explain);
			response_map.put("version", version_explain);
		}
		
		if (response_json == null) {
			//The function did not directly set the json code. We'll create it from the 
			//response_map function.
			try {
				JSONObject conversion = new JSONObject(response_map);
				response_json = conversion.toString(3);
			}
			catch (JSONException e) {
				response_json = "{\"error\":\"Error occurred, please notify webmaster (at) jsontest (dot) com\"}";
			}
		}
		
		//Wrap with callback if necessary
		String callback = req.getParameter("callback");
		if (callback != null) {
			response_json = callback + "(" + response_json + ");";
		}
		
		/**
		 * By default, we return an application type of application/json.
		 * However, for testing purposes the user may prefer a plain text return type.
		 */
		String return_type = req.getParameter("type");
		if ("text".equals(return_type)) {
			resp.setContentType("text/plain");
		}
		else {
			resp.setContentType("application/json");
		}
		
		resp.getWriter().print(response_json);
		
	}//end doPost
	
	public String generateGenericJSON() {
		//Create our "good" JSON response
		JSONObject obj = new JSONObject();
		
		String response_json = "";
		
		try {
			obj.put("url", "http://www.jsontest.com");
			obj.put("number", 42);
			obj.put("yesorno", true);
			
			//We create our malformed JSON by cutting out the last } marker.
			response_json = obj.toString(3);
			response_json = response_json.trim();
		}
		catch (JSONException e) {
			//This exception should never be thrown, unfortunately we have to catch it because 
			//JSONObject.put is declared to be able to throw a JSONException, which only happens
			//if the key is null, which will never happen.
			System.out.println("Error in generating generic JSON");
		}
		
		return response_json;
	}
	
	
	public static Vector<String> getFirstLevelKeys(JSONObject json_object) {
		Vector<String> key_list = new Vector<String>();
		
		Iterator<String> iterate = json_object.keys();
		
		while (iterate.hasNext()) {
			String key = iterate.next();
			key_list.add(key);
		}
		
		return key_list;
	}
	
	
}//end class
