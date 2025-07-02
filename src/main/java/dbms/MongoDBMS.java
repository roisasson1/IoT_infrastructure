package dbms;

import com.google.gson.JsonPrimitive;
import com.mongodb.*;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Date;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MongoDBMS implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(MongoDBMS.class);
    private final MongoClient mongoClient;
    private static final Map<String, MongoDatabase> dbCache = new ConcurrentHashMap<>();

    public MongoDBMS(String connectionString) {
        Objects.requireNonNull(connectionString, "MongoDB connection string cannot be null.");

        ServerApi serverApi = ServerApi.builder()
                .version(ServerApiVersion.V1)
                .build();

        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(connectionString))
                .serverApi(serverApi)
                .build();

        this.mongoClient = MongoClients.create(settings);
        System.out.println("MongoDB client initialized for connection string: " + connectionString);
        logger.info("MongoDB client initialized for connection string: {}", connectionString);
    }

    public MongoDatabase getDatabase(String dbName) {
        Objects.requireNonNull(dbName, "Database name cannot be null.");
        logger.debug("Attempting to get or create database: {}", dbName);
        return dbCache.computeIfAbsent(dbName, mongoClient::getDatabase);
    }

    public boolean registerCompanyDB(String companyName, String compId) {
        String fullCompanyName = companyName + "_" + compId;
        String dbName = cleanNameForMongo(fullCompanyName);
        logger.info("Attempting to register company DB for: {} (Cleaned DB Name: {})", fullCompanyName, dbName);
        try {
            MongoDatabase database = getDatabase(dbName);

            boolean collectionExists = database.listCollectionNames().into(new ArrayList<>()).contains("metadata");
            if (!collectionExists) {
                database.createCollection("metadata");
                System.out.println("Created 'metadata' collection in DB: " + dbName);
                logger.info("Created 'metadata' collection in DB: {}", dbName);
                MongoCollection<Document> metadataCollection = database.getCollection("metadata");
                Document initialMetadata = new Document("created_at", new Date())
                        .append("company_id", compId)
                        .append("company_name", companyName);
                metadataCollection.insertOne(initialMetadata);
                System.out.println("Inserted initial metadata for company: " + fullCompanyName);
                logger.info("Inserted initial metadata for company: {}", fullCompanyName);
            } else {
                System.out.println("Metadata collection already exists in DB: " + dbName);
                logger.info("Metadata collection already exists in DB: {}", dbName);
            }
            return true;
        } catch (Exception e) {
            System.err.println("Error registering company DB for " + companyName + " (ID: " + compId + "): " + e.getMessage());
            logger.error("Error registering company DB for {} (ID: {}): {}", companyName, compId, e.getMessage(), e);
            return false;
        }
    }

    public MongoCollection<Document> registerProductCollection(String companyName, String companyId, String productName, String version) {
        String fullCompanyName = companyName + "_" + companyId;
        String dbName = cleanNameForMongo(fullCompanyName);
        String collectionName = generateProductCollectionName(productName, version);
        logger.info("Attempting to register product collection: {} (v{}) for company: {} (DB: {})", productName, version, fullCompanyName, dbName);
        try {
            MongoDatabase database = getDatabase(dbName);

            // check if the collection exists
            if (!database.listCollectionNames().into(new ArrayList<>()).contains(collectionName)) {
                database.createCollection(collectionName);
                System.out.println("Created product collection: " + collectionName + " in DB: " + dbName);
                logger.info("Created product collection: {} in DB: {}", collectionName, dbName);

                MongoCollection<Document> newProductCollection = database.getCollection(collectionName);
                Document initialMetadata = new Document("metadata",
                        new Document("created_at", new Date())
                                .append("company_name", companyName)
                                .append("company_id", companyId)
                                .append("product_name", productName)
                                .append("product_version", version));
                newProductCollection.insertOne(initialMetadata);
                System.out.println("Inserted initial metadata into product collection: " + collectionName);
                logger.info("Inserted initial metadata into product collection: {}", collectionName);

            } else {
                System.out.println("Product collection already exists: " + collectionName + " in DB: " + dbName);
                logger.info("Product collection already exists: {} in DB: {}", collectionName, dbName);
            }
            return database.getCollection(collectionName);
        } catch (Exception e) {
            System.err.println("Error registering product collection " + productName + " (v" + version + ") for company " + companyName + " (ID: " + companyId + "): " + e.getMessage());
            logger.error("Error registering product collection {} (v{}) for company {} (ID: {}): {}", productName, version, companyName, companyId, e.getMessage(), e);
            return null;
        }
    }

    private String generateIoTUpdateCollectionName(String prodName, String version, String iotId) {
        // Example: apple_watch_3_0_iot-device-001_updates
        return cleanNameForMongo(prodName + "_" + version + "_" + iotId + "_updates");
    }

    public void createIoTUpdateCollection(String companyName, String compId, String prodName, String version, String iotId) throws IOException {
        String dbName = cleanNameForMongo(companyName + "_" + compId);
        String collectionName = generateIoTUpdateCollectionName(prodName, version, iotId);
        logger.info("Attempting to create IoT update collection: {} for IoT: {} (DB: {})", collectionName, iotId, dbName);

        try {
            MongoDatabase database = getDatabase(dbName);
            if (!database.listCollectionNames().into(new ArrayList<>()).contains(collectionName)) {
                database.createCollection(collectionName);
                System.out.println("Created IoT update collection: " + collectionName + " in DB: " + dbName);
                logger.info("Created IoT update collection: {} in DB: {}", collectionName, dbName);
            } else {
                System.out.println("IoT update collection already exists: " + collectionName + " in DB: " + dbName);
                logger.info("IoT update collection already exists: {} in DB: {}", collectionName, dbName);
            }
        } catch (Exception e) {
            String errorMessage = String.format(
                    "Error creating IoT update collection %s (v%s) for IoT %s for company %s (ID: %s): %s",
                    prodName, version, iotId, companyName, compId, e.getMessage()
            );
            System.err.println(errorMessage);
            logger.error(errorMessage, e);
            throw new IOException("Failed to create IoT update collection: " + collectionName, e);
        }
    }

    public Document registerIoTDevice(String companyName, String compId, String prodName, String version, String iotId, JsonObject extraData) {
        String companyDbName = cleanNameForMongo(companyName + "_" + compId);
        String productCollectionName = generateProductCollectionName(prodName, version);
        logger.info("Attempting to register IoT device: {} in collection: {} for company: {} (DB: {})", iotId, productCollectionName, companyName, companyDbName);

        Document iotDevice = null;
        boolean deviceAlreadyExists = false;

        try {
            MongoDatabase database = mongoClient.getDatabase(companyDbName);
            MongoCollection<Document> productCollection = database.getCollection(productCollectionName);

            // check if the collection actually exists
            if (!database.listCollectionNames().into(new ArrayList<>()).contains(productCollectionName)) {
                String errorMessage = String.format("Error: Product collection '%s' does not exist for company '%s'. Register product first.", productCollectionName, companyDbName);
                System.err.println(errorMessage);
                logger.error(errorMessage);
                return null;
            }

            iotDevice = new Document("_id", iotId)
                    .append("created_at", new Date())
                    .append("company_id", compId)
                    .append("company_name", companyName)
                    .append("product_name", prodName)
                    .append("product_version", version);

            if (extraData != null && !extraData.entrySet().isEmpty()) {
                Document extraFieldsDoc = convertJsonToDocument(extraData);
                for (Map.Entry<String, Object> entry : extraFieldsDoc.entrySet()) {
                    iotDevice.append(entry.getKey(), entry.getValue());
                }
                logger.debug("Appending extra data to IoT device document: {}", iotDevice);
            }

            productCollection.insertOne(iotDevice);
            System.out.println("IoT device " + iotId + " successfully registered in collection " + productCollectionName + " for company " + companyDbName);
            logger.info("IoT device {} successfully registered in collection {} for company {}", iotId, productCollectionName, companyDbName);

        } catch (MongoException e) {
            if (e.getMessage() != null && e.getMessage().contains("E11000 duplicate key error")) {
                deviceAlreadyExists = true;
                System.err.println("IoT Device " + iotId + " already exists in MongoDB for company " + companyName + ", product " + prodName + " (v" + version + ").");
                logger.warn("IoT Device {} already exists in MongoDB for company {}, product {} (v{}).", iotId, companyName, prodName, version);
            } else {
                System.err.println("Error registering IoT device " + iotId + " in MongoDB: " + e.getMessage());
                logger.error("Error registering IoT device {} in MongoDB: {}", iotId, e.getMessage(), e);
                return null;
            }
        }

        try {
            createIoTUpdateCollection(companyName, compId, prodName, version, iotId);
        } catch (IOException e) {
            String warningMsg = String.format("Failed to ensure IoT update collection for %s device %s. This might indicate a configuration issue. Error: %s",
                    deviceAlreadyExists ? "existing" : "newly registered", iotId, e.getMessage());
            System.err.println("Warning: " + warningMsg);
            logger.warn(warningMsg, e);
        } catch (RuntimeException e) {
            String warningMsg = String.format("Unexpected error while ensuring IoT update collection for %s device %s. Error: %s",
                    deviceAlreadyExists ? "existing" : "newly registered", iotId, e.getMessage());
            System.err.println("Warning: " + warningMsg);
            logger.error(warningMsg, e);
        }
        return deviceAlreadyExists ? null : iotDevice;
    }

    public boolean updateIoTDevice(String companyName, String compId, String prodName, String version, String iotId, Document updateData) {
        String companyDbName = cleanNameForMongo(companyName + "_" + compId);
        String iotUpdateCollectionName = generateIoTUpdateCollectionName(prodName, version, iotId);
        logger.info("Attempting to log update for IoT device: {} in collection: {} for company: {} (DB: {})", iotId, iotUpdateCollectionName, companyName, companyDbName);
        try {
            MongoDatabase database = mongoClient.getDatabase(companyDbName);
            MongoCollection<Document> updatesCollection = database.getCollection(iotUpdateCollectionName);

            if (!database.listCollectionNames().into(new ArrayList<>()).contains(iotUpdateCollectionName)) {
                System.err.println("Error: IoT update collection '" + iotUpdateCollectionName + "' not found for company '" + companyDbName + "'. Cannot log update for IoT device " + iotId + ". Ensure device is registered and its update collection created.");
                logger.error("Error: IoT update collection '{}' not found for company '{}'. Cannot log update for IoT device {}. Ensure device is registered and its update collection created.", iotUpdateCollectionName, companyDbName, iotId);
                return false;
            }

            if (!updateData.containsKey("timestamp")) {
                updateData.append("timestamp", new Date());
                logger.debug("Added timestamp to update data for IoT device {}: {}", iotId, updateData.getDate("timestamp"));
            }

            updatesCollection.insertOne(updateData);
            System.out.println("Logged update for IoT device " + iotId + " into collection: " + iotUpdateCollectionName + " in DB: " + companyDbName);
            logger.info("Logged update for IoT device {} into collection: {} in DB: {}", iotId, iotUpdateCollectionName, companyDbName);
            return true;

        } catch (MongoException e) {
            System.err.println("Error logging update for IoT device " + iotId + " in MongoDB for company " + companyName + " (ID: " + compId + "): " + e.getMessage());
            logger.error("Error logging update for IoT device {} in MongoDB for company {} (ID: {}): {}", iotId, companyName, compId, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public void close() {
        if (mongoClient != null) {
            mongoClient.close();
            System.out.println("MongoDB client closed.");
            logger.info("MongoDB client closed.");
        } else {
            logger.warn("Attempted to close MongoDB client, but it was null.");
        }
    }

    private static Document convertJsonToDocument(JsonObject jsonObject) {
        Document document = new Document();
        logger.debug("Converting JsonObject to Document: {}", jsonObject.toString());
        jsonObject.entrySet().forEach(entry -> {
            String key = entry.getKey();
            com.google.gson.JsonElement value = entry.getValue();

            if (value.isJsonPrimitive()) {
                JsonPrimitive primitive = value.getAsJsonPrimitive();
                if (primitive.isBoolean()) {
                    document.append(key, primitive.getAsBoolean());
                    logger.trace("Appended boolean '{}': {}", key, primitive.getAsBoolean());
                } else if (primitive.isNumber()) {
                    try {
                        document.append(key, primitive.getAsInt());
                        logger.trace("Appended int '{}': {}", key, primitive.getAsInt());
                    } catch (NumberFormatException e1) {
                        try {
                            document.append(key, primitive.getAsLong());
                            logger.trace("Appended long '{}': {}", key, primitive.getAsLong());
                        } catch (NumberFormatException e2) {
                            try {
                                document.append(key, primitive.getAsDouble());
                                logger.trace("Appended double '{}': {}", key, primitive.getAsDouble());
                            } catch (NumberFormatException e3) {
                                document.append(key, primitive.getAsString());
                                System.err.println("Warning: Could not parse number '" + primitive.getAsString() + "' for key '" + key + "' as int, long, or double. Stored as String.");
                                logger.warn("Could not parse number '{}' for key '{}' as int, long, or double. Stored as String.", primitive.getAsString(), key);
                            }
                        }
                    }
                } else if (primitive.isString()) {
                    document.append(key, primitive.getAsString());
                    logger.trace("Appended string '{}': {}", key, primitive.getAsString());
                } else if (primitive.isJsonNull()) {
                    document.append(key, null);
                    logger.trace("Appended null for key '{}'", key);
                } else {
                    document.append(key, primitive.getAsString());
                    logger.trace("Appended primitive as string for key '{}': {}", key, primitive.getAsString());
                }
            } else if (value.isJsonNull()) {
                document.append(key, null);
                logger.trace("Appended null for key '{}' (JsonNull)", key);
            } else if (value.isJsonArray()) {
                System.err.println("Warning: Skipping JSON Array for key: " + key);
                logger.warn("Skipping JSON Array for key: {}", key);
            } else if (value.isJsonObject()) {
                System.err.println("Warning: Skipping nested JSON Object for key: " + key);
                logger.warn("Skipping nested JSON Object for key: {}", key);
            }
        });
        logger.debug("Finished converting JsonObject to Document. Result: {}", document);
        return document;
    }

    private String cleanNameForMongo(String name) {
        String cleaned = name.replaceAll("[^a-zA-Z0-9_.-]", "_")
                .replaceAll("\\s+", "_")
                .toLowerCase();
        logger.debug("Cleaned name '{}' to '{}' for MongoDB", name, cleaned);
        return cleaned;
    }

    private String generateProductCollectionName(String prodName, String version) {
        String generatedName = cleanNameForMongo(prodName + "_" + version + "_iots");
        logger.debug("Generated product collection name for product '{}' (v{}) as '{}'", prodName, version, generatedName);
        return generatedName;
    }
}