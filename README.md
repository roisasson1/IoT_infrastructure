# IoT Infrastructure
This project delivers a robust and flexible IoT infrastructure designed to facilitate seamless communication between diverse smart devices and managing applications. It enables companies to collect, store, manage, and process real-time data from their IoT devices efficiently, without the need to build complex backend systems from scratch.

## Core API
Clients interact with the system via a simple RESTful API for device and data management:

registerCompany: Register a new company.

registerProduct: Register a new product type.

registerIoT: Register a specific IoT device in the field.

updateIoT: Send real-time updates from a registered device.

### Example Use Case: Smart Lamp
A company registers its "Smart Lamp" product. When a user installs the lamp, it registers to the system, then continuously sends updates (e.g., status, brightness) via the updateIoT endpoint.

## Architecture Highlights
The system follows a microservices-oriented architecture, primarily featuring:

### Gateway: Acts as the entry point for all device updates.

**Connection Service (CS)**: Listens to TCP/UDP/HTTP protocols, using a Selector for efficient connection handling.

**Request Process Service (RPS)**: Processes incoming requests. It leverages a Thread Pool for concurrent task execution and design patterns like Parser, Factory, and Command Pattern for flexible and extensible logic handling.

### Databases:

**Admin DB (Relational)**: Stores static data about companies and products.

**Company-specific DBMS (Non-Relational)**: Stores dynamic, real-time IoT data for each company, optimized for high-speed writes and flexible data structures.

### Plug & Play mechanism
Allows dynamic loading of new commands via JAR files into a dedicated folder, utilizing the Observer Pattern, Class Loader, and Mediator Pattern to seamlessly integrate new functionality without downtime. A Watchdog component monitors the Gateway and performs automatic restarts in case of failure.

## Technologies Used
**Core Language**: Java

**Networking**: TCP, UDP, HTTP (using Selector for non-blocking I/O)

**Design Patterns**: Thread Pool, Parser, Factory, Command, Observer, Mediator

**Databases**: Relational DB (for admin data), Non-Relational DBs (for IoT data)

## Getting Started
To set up and run the project locally:

### Prerequisites:

Java Development Kit (JDK) [Version, e.g., 17+]

Maven [Version]

[Database 1, e.g., PostgreSQL] running locally

[Database 2, e.g., MongoDB] running locally

Clone the Repository:

Bash

git clone [repository-url]
cd [project-folder]
Configure Databases:

Update application.properties (or similar config file) with your database connection details.

Ensure your databases are running and accessible.

## Build the Project:

Bash

mvn clean install
Run the Application:

Bash

java -jar target/[your-jar-file-name].jar
Access the API:

The API endpoints will be available at http://localhost:[port]/api/... (adjust port if needed).

Refer to the API documentation (if available, or infer from Core API section above) for specific endpoint usage.
