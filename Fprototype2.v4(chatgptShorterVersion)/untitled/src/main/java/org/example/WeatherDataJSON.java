package org.example;
import org.json.JSONArray;
import org.json.JSONObject;
import java.sql.*;

public class WeatherDataJSON {

    private static final String DB_URL = "jdbc:postgresql://localhost:5432/WeatherApp";
    private static final String USER = "postgres";
    private static final String PASSWORD = "root";

    public static void main(String[] args) {
        JSONArray weatherData = retrieveWeatherData();
        if (weatherData != null) {
            System.out.println(weatherData.toString());
        } else {
            System.out.println("Failed to retrieve weather data from PostgreSQL.");
        }
    }

    private static JSONArray retrieveWeatherData() {
        JSONArray jsonArray = new JSONArray();
        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD)) {
            String sql = "SELECT * FROM weatherdata";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                ResultSet resultSet = statement.executeQuery();
                while (resultSet.next()) {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("city", resultSet.getString("city"));
                    jsonObject.put("temperature", resultSet.getDouble("temperature"));
                    jsonObject.put("humidity", resultSet.getDouble("humidity"));
                    jsonArray.put(jsonObject);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return jsonArray;
    }
}
