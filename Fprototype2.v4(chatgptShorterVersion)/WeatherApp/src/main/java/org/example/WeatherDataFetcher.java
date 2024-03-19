package org.example;
import org.json.JSONObject; // Importing the JSONObject class from the org.json package
import java.io.BufferedReader; // For reading text from a character-input stream
import java.io.IOException; // For handling input/output errors
import java.io.InputStreamReader; // For converting bytes to characters
import java.net.HttpURLConnection; // For making HTTP requests
import java.net.URL; // For working with URLs
import java.sql.*; // For working with databases
import java.time.Duration; // For representing a time span
import java.time.LocalDateTime; // For representing date and time without a time zone

public class WeatherDataFetcher {

    private static final String API_KEY = "df9c5a09de88be31f5a79433b188fdf7"; // Unique key for accessing OpenWeatherMap API
    private static final String API_URL = "http://api.openweathermap.org/data/2.5/weather?q=%s&appid=%s&units=metric"; // URL for OpenWeatherMap API
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/WeatherApp"; // Database URL
    private static final String USER = "postgres"; // Database username
    private static final String PASSWORD = "root"; // Database password

    public static void main(String[] args) {
        String city = "Andijan"; // The city for which we want to fetch weather data

        // Retrieve existing weather data from the database
        JSONObject savedWeatherData = retrieveWeatherData(city);

        // Check if existing data is recent, if yes, use it, if not, fetch new data
        if (savedWeatherData != null && isWeatherDataRecent(savedWeatherData)) {
            // Data is recent, use the existing data
            System.out.println("Received weather data from database: " + savedWeatherData.toString());
        } else {
            // Data is not recent or doesn't exist, fetch new data from API
            JSONObject weatherData = fetchWeatherData(city);
            if (weatherData != null) {
                insertOrUpdateWeatherData(weatherData);
                System.out.println("New weather data inserted into PostgreSQL database.");
                System.out.println("Received weather data from API: " + weatherData.toString());
            } else {
                System.out.println("Failed to fetch weather data from API.");
            }
        }
    }

    // Method to fetch weather data from the OpenWeatherMap API
    public static JSONObject fetchWeatherData(String city) {
        try {
            // Create the URL for the API request by formatting the API_URL with city name and API key
            URL url = new URL(String.format(API_URL, city, API_KEY));

            // Open a connection to the URL
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // Set the request method to GET
            connection.setRequestMethod("GET");

            // Read the API response
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            // Parse the JSON response
            JSONObject jsonResponse = new JSONObject(response.toString());
            JSONObject main = jsonResponse.getJSONObject("main");
            double temperature = main.getDouble("temp");
            double humidity = main.getDouble("humidity");

            // Create a JSON object for weather data
            JSONObject weatherData = new JSONObject();
            weatherData.put("city", city);
            weatherData.put("temperature", temperature);
            weatherData.put("humidity", humidity);

            return weatherData; // Return the weather data
        } catch (IOException e) {
            // Handle IO errors if any
            System.err.println("Error fetching weather data from API: " + e.getMessage());
        }
        return null; // Return null if there was an error or no data fetched
    }

    // Method to check if weather data is recent in the database
    public static boolean isWeatherDataRecent(JSONObject weatherData) {
        try {
            // Extract the last updated timestamp from the weather data
            Timestamp lastUpdated = Timestamp.valueOf(weatherData.getString("last_updated"));

            // Get the current time
            LocalDateTime now = LocalDateTime.now();

            // Convert the timestamp to local date time
            LocalDateTime lastUpdate = lastUpdated.toLocalDateTime();

            // Calculate the duration between now and last update
            Duration duration = Duration.between(lastUpdate, now);

            // Get the minutes difference
            long minutes = duration.toMinutes();

            // Check if the data is less than 60 minutes (1 hour) old
            return minutes < 60;
        } catch (Exception e) {
            // Handle errors if any
            System.err.println("Error checking weather data timestamp: " + e.getMessage());
        }
        return false; // Return false if there was an error or data is not recent
    }

    // Method to insert or update weather data in the database
    public static void insertOrUpdateWeatherData(JSONObject weatherData) {
        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD)) {
            // SQL query to insert or update weather data
            String sql = "INSERT INTO \"weatherData\" (city, temperature, humidity, last_updated) " +
                "VALUES (?, ?, ?, ?) " +
                "ON CONFLICT (city) DO UPDATE SET temperature = excluded.temperature, " +
                "humidity = excluded.humidity, last_updated = excluded.last_updated";

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                // Set the values for the query
                statement.setString(1, weatherData.getString("city"));
                statement.setDouble(2, weatherData.getDouble("temperature"));
                statement.setDouble(3, weatherData.getDouble("humidity"));

                // Convert last updated time to timestamp
                Timestamp lastUpdated = Timestamp.valueOf(weatherData.getString("last_updated"));
                statement.setTimestamp(4, lastUpdated);

                // Execute the query
                statement.executeUpdate();

                // Print success message
                System.out.println("Weather data inserted or updated in PostgreSQL database.");
            }
        } catch (SQLException e) {
            // Handle SQL errors if any
            System.err.println("Error inserting or updating data in database: " + e.getMessage());
        }
    }

    // Method to retrieve weather data from the database
    public static JSONObject retrieveWeatherData(String city) {
        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD)) {
            // SQL query to retrieve weather data for the city
            String sql = "SELECT * FROM \"weatherData\" WHERE city = ?";

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                // Set the city parameter
                statement.setString(1, city);

                // Execute the query
                ResultSet resultSet = statement.executeQuery();

                // Check if data exists
                if (resultSet.next()) {
                    // Create a JSON object for weather data
                    JSONObject weatherData = new JSONObject();
                    weatherData.put("city", resultSet.getString("city"));
                    weatherData.put("temperature", resultSet.getDouble("temperature"));
                    weatherData.put("humidity", resultSet.getDouble("humidity"));
                    weatherData.put("last_updated", resultSet.getTimestamp("last_updated").toString());

                    // Return the weather data
                    return weatherData;
                }
            }
        } catch (SQLException e) {
            // Handle SQL errors if any
            System.err.println("Error retrieving data from database: " + e.getMessage());
        }
        return null; // Return null if no data found or error occurred
    }
}
