package org.example;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.*;
import java.time.Duration;
import java.time.LocalDateTime;

public class WeatherDataFetcher {

    private static final String API_KEY = "df9c5a09de88be31f5a79433b188fdf7";
    private static final String API_URL = "http://api.openweathermap.org/data/2.5/weather?q=%s&appid=%s&units=metric";

    private static final String DB_URL = "jdbc:postgresql://localhost:5432/WeatherApp";
    private static final String USER = "postgres";
    private static final String PASSWORD = "root";

    public static void main(String[] args) {
        String city = "Andijan"; // Change to the desired city

        // Retrieve existing weather data from the database
        JSONObject savedWeatherData = retrieveWeatherData(city);

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
            URL url = new URL(String.format(API_URL, city, API_KEY));
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            // Read the API response
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            // Parse JSON response
            JSONObject jsonResponse = new JSONObject(response.toString());
            JSONObject main = jsonResponse.getJSONObject("main");
            double temperature = main.getDouble("temp");
            double humidity = main.getDouble("humidity");

            // Create JSON object for weather data
            JSONObject weatherData = new JSONObject();
            weatherData.put("city", city);
            weatherData.put("temperature", temperature);
            weatherData.put("humidity", humidity);

            return weatherData;
        } catch (IOException e) {
            System.err.println("Error fetching weather data from API: " + e.getMessage());
        }
        return null;
    }

    // Method to check if weather data is recent in the database
    public static boolean isWeatherDataRecent(JSONObject weatherData) {
        try {
            Timestamp lastUpdated = Timestamp.valueOf(weatherData.getString("last_updated"));
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime lastUpdate = lastUpdated.toLocalDateTime();
            Duration duration = Duration.between(lastUpdate, now);
            long minutes = duration.toMinutes(); // Calculate minutes
            return minutes < 60; // If less than 60 minutes (1 hour) old
        } catch (Exception e) {
            System.err.println("Error checking weather data timestamp: " + e.getMessage());
        }
        return false; // If any error occurs, consider data as not recent
    }

    // Method to insert or update weather data in the database
    public static void insertOrUpdateWeatherData(JSONObject weatherData) {
        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD)) {
            String sql = "INSERT INTO \"weatherData\" (city, temperature, humidity, last_updated) " +
                "VALUES (?, ?, ?, ?) " +
                "ON CONFLICT (city) DO UPDATE SET temperature = excluded.temperature, " +
                "humidity = excluded.humidity, last_updated = excluded.last_updated";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, weatherData.getString("city"));
                statement.setDouble(2, weatherData.getDouble("temperature"));
                statement.setDouble(3, weatherData.getDouble("humidity"));
                Timestamp lastUpdated = Timestamp.valueOf(weatherData.getString("last_updated"));
                statement.setTimestamp(4, lastUpdated);
                statement.executeUpdate();
                System.out.println("Weather data inserted or updated in PostgreSQL database.");
            }
        } catch (SQLException e) {
            System.err.println("Error inserting or updating data in database: " + e.getMessage());
        }
    }

    // Method to retrieve weather data from the database
    public static JSONObject retrieveWeatherData(String city) {
        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD)) {
            String sql = "SELECT * FROM \"weatherData\" WHERE city = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, city);
                ResultSet resultSet = statement.executeQuery();
                if (resultSet.next()) {
                    JSONObject weatherData = new JSONObject();
                    weatherData.put("city", resultSet.getString("city"));
                    weatherData.put("temperature", resultSet.getDouble("temperature"));
                    weatherData.put("humidity", resultSet.getDouble("humidity"));
                    weatherData.put("last_updated", resultSet.getTimestamp("last_updated").toString());
                    return weatherData;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving data from database: " + e.getMessage());
        }
        return null;
    }
}
