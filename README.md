# Finance Tracker ML

A mobile finance tracking application that integrates Android development, Firebase authentication, and machine learning models to analyze spending patterns.

## Tech Stack

Frontend:
- Android Studio
- Java
- XML Layouts
- Material UI

Backend:
- Python
- FastAPI

Machine Learning:
- Scikit-Learn
- Pandas
- NumPy

Database:
- Firebase / SQL

## Setup Instructions

### 1 Clone Repository

git clone https://github.com/Subah21/finance-tracker-ml.git

### 2 Open Project

Open the project folder in Android Studio.

### 3 Add Firebase Configuration

Place the file:

app/google-services.json

inside the app directory.


# Firebase Setup (Required for Team Members)

This project uses **Firebase Authentication**.  
After cloning or pulling the repository, you must configure Firebase locally before running the app.

---

## Step 1 — Pull the Latest Project

If you already cloned the repository:

git pull origin main

If this is your first time:

git clone https://github.com/Subah21/finance-tracker-ml.git
cd finance_tracker

---

## Step 2 — Download the Firebase Configuration File

Firebase requires a configuration file called:

google-services.json

To download it:

1. Go to the Firebase Console  
   https://console.firebase.google.com

2. Open the project used for this app

3. Click the **gear icon (Project Settings)**

4. Scroll down to **Your Apps**

5. Select the **Android app**

6. Click **Download google-services.json**

Firebase generates this file so the Android app can connect to the correct Firebase project. 

---


//////////////////////////

## Step 3 — Add the File to the Android App

Move the downloaded file into this directory:

finance_tracker/app/google-services.json

Your project structure should now look like:

finance_tracker
│
├── app
│   ├── src
│   ├── res
│   └── google-services.json
│
├── backend_API
├── backend_ML_models
└── README.md

The configuration file **must be placed inside the `app` module directory** so the Firebase Gradle plugin can read it during the build process.

---

## Step 4 — Sync Gradle

Open the project in **Android Studio** and run:

File → Sync Project with Gradle Files

This loads the Firebase SDK and configuration.

---

## Step 5 — Run the Application

After syncing the project, run the app using an emulator or Android device.

You should now be able to:

• Create a new account  
• Log in  
• Use Firebase authentication

---

## Troubleshooting

If the app fails to build or Firebase does not work:

• Ensure the file name is exactly `google-services.json`  
• Ensure it is located inside the `app/` folder  
• Sync Gradle again

Firebase requires this configuration file to initialize the SDK and connect the Android app to the Firebase project.

//////////////////////////////

### 4 Sync Gradle

Click "Sync Project with Gradle Files".

### 5 Run the App

Run the project using an emulator or Android device.

The application will launch the login screen.

## Current Features

- User authentication
- Firebase login and signup
- Dashboard UI
- Navigation between screens

## Upcoming Features

- Spending analytics
- Machine learning predictions
- FastAPI backend integration
