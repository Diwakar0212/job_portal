# 🚀 JobHook - Job Portal Web Application

A full-stack Job Portal platform built with **React (TypeScript)**, **Spring Boot**, and **MongoDB**.

---

## 🏗️ Architecture & Tech Stack

- **Frontend:** React 18, TypeScript, Tailwind CSS, Mantine UI, Redux Toolkit, TipTap Editor, Tabler Icons.
- **Backend:** Java 17+, Spring Boot 3.3.2, Spring Security, JJWT (Stateless Auth), Spring Data MongoDB, JavaMailSender (OTP via SMTP).
- **Database:** MongoDB running on default port `27017`.

---

## 📋 Prerequisites

Make sure the following tools are installed on your machine:
- **Node.js:** v18 or later (tested with v22.x) and `npm`
- **Java Development Kit (JDK):** Java 17 or higher (tested with Java 24)
- **Docker Desktop:** For running MongoDB container (or standalone MongoDB Community Server)

---

## 🛠️ Initial Setup (Run Once)

### 1. Start MongoDB via Docker

1. Ensure **Docker Desktop** is open and running.
2. Open PowerShell or Command Prompt in the project root and create the container:
   ```powershell
   docker run -d -p 27017:27017 --name mongodb mongo:latest
   ```
3. *(Recommended)* Configure the container to start automatically whenever Docker starts:
   ```powershell
   docker update --restart unless-stopped mongodb
   ```

### 2. Initialize Sequence Counters in MongoDB
The application requires auto-incrementing counters in a `sequence` collection to assign IDs to users, profiles, jobs, and notifications. Run this once inside the MongoDB container:

```powershell
docker exec mongodb mongosh jobportal --eval "db.sequence.insertMany([{ _id: 'users', seq: NumberLong(0) }, { _id: 'profiles', seq: NumberLong(0) }, { _id: 'jobs', seq: NumberLong(0) }, { _id: 'notification', seq: NumberLong(0) }])"
```

### 3. Install Frontend Dependencies
From the project root:

```powershell
cd frontend
npm install --legacy-peer-deps
```

> **Note:** Use `--legacy-peer-deps` to ensure Mantine v7 and React 18 peer dependencies resolve smoothly.

---

## ▶️ Daily Running Guide

To run the complete application, you will keep **two terminals** open:

### Terminal 1: Run Backend (Spring Boot)

1. Navigate to the `backend` folder:
   ```powershell
   cd backend
   ```
2. Launch Spring Boot using the Maven wrapper:
   ```powershell
   .\mvnw.cmd spring-boot:run
   ```
   *(On macOS / Linux use `./mvnw spring-boot:run`)*
3. The API will start on **`http://localhost:8081`**.

---

### Terminal 2: Run Frontend (React)

1. Open a second terminal window and navigate to `frontend`:
   ```powershell
   cd frontend
   ```
2. Start the development server:
   ```powershell
   npm start
   ```
3. Your browser will automatically open **`http://localhost:3000`**.

---

## ⚙️ Configuration Details

### Backend Configuration
Located in: [`backend/src/main/resources/application.properties`](backend/src/main/resources/application.properties)

- **Port:** `8081`
- **Database URI:** `mongodb://localhost:27017/jobportal`
- **Mail (SMTP):** Used for sending OTPs for password recovery. Configure your Gmail App Password if needed:
  ```properties
  spring.mail.username=your-email@gmail.com
  spring.mail.password=your-app-password
  ```

### Frontend Configuration
- **API URL:** Default base URL is `http://localhost:8081/`. Service endpoints are configured in [`frontend/src/Services/`](frontend/src/Services/).

---

## 🔍 Managing Docker MongoDB

- **Check if MongoDB container is running:**
  ```powershell
  docker ps
  ```
- **Start container (if stopped after reboot):**
  ```powershell
  docker start mongodb
  ```
- **Stop container:**
  ```powershell
  docker stop mongodb
  ```
- **Open MongoDB Shell (mongosh):**
  ```powershell
  docker exec -it mongodb mongosh jobportal
  ```

---

## ❓ Troubleshooting

| Issue | Cause | Solution |
|---|---|---|
| `docker: error during connect: Head ... open //./pipe/...` | Docker Desktop application is closed. | Launch Docker Desktop from Windows Start Menu and wait for the engine to start. |
| `Registration Failed` (Sequence error) | Empty MongoDB without `sequence` collection. | Run the sequence initialization command in Step 2 of Setup. |
| `npm error enoent Could not read package.json` | Running `npm` from the root instead of `frontend`. | Change directory: `cd frontend` before running npm commands. |
| Port conflict on `8081` or `3000` | Another process is occupying the port. | Check running processes or terminate previously running instances before starting. |
| Corrupted or missing modules | Dependencies unzipped from another machine. | Run `Remove-Item -Recurse -Force node_modules` in `frontend` and re-run `npm install --legacy-peer-deps`. |
