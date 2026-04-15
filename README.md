<p align="center">
  <img src="imgs/logo/logo_mpc.jpg" width="200" height="200" alt="MultiProtocolClient Logo">
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Material%20You-Enabled-blue">
  <img src="https://img.shields.io/badge/platform-Android-brightgreen">
  <img src="https://img.shields.io/badge/kotlin-100%25-blueviolet">
</p>

<h1 align="center">Multi Protocol Client for Android</h1>

<p align="center">
  A modern and versatile Android application that allows interaction with different network protocols through an elegant and intuitive Material You interface.
</p>

---

## ✨ Features

### 🔒 HTTP/HTTPS
- GET requests
- SSL/TLS support
- Self-signed certificate handling
- Status code display
- Complete response body

### 🕒 NTP (Network Time Protocol)
- Multiple timezone support
- Automatic system timezone detection
- Millisecond precision

### 🛠 Custom TCP/UDP
- Custom TCP connections
- UDP packets
- Real-time response display

### 📡 DNS Management
- DNS queries
- Record lookup (A, MX, CNAME, NS, PTR, ANY)
- DNS over HTTPS
- DNS over TLS
- DNS over QUIC
- DNS query using TCP
- DNS Recursion

### 📊 Network Diagnostics
- Ping
- Traceroute

### 📧 Email Protocols
- SMTP (25/587)
- POP3 (110/995)
- IMAP (143/993)

### 🔐 Remote Access
- Telnet (Interactive Terminal)
- SSH (Secure Shell with password auth)

### 🌍 Network Tools
- Wake-on-LAN (WoL) Magic Packet sender
- WHOIS Domain Registrant Lookup (Port 43)

### 📊 Monitoring & IoT
- SNMP (Simple Network Management Protocol v2c)
- MQTT (Message Queuing Telemetry Transport with SSL/TLS support)

### Performance Testing
- iPerf3 client (TCP/UDP and reverse mode)
- iPerf2 client (TCP/UDP)
- Bundled binary loading by ABI (`assets/iperf/<abi>/`) with automatic extraction and execute permission

### 📁 File Transfer
- FTP (Active/Passive)
- SFTP (Secure File Transfer)
- TFTP (UDP Port 69 configuration fetcher)

### 🔍 Discovery & Router Management
- mDNS / Bonjour / SSDP (Network services & device discovery)
- Dedicated mDNS / Bonjour service discovery by service type
- UPnP (IGD External IP and router discovery)

### Security and Troubleshooting
- TCP Port Scanner with configurable port range and timeout

## 🎨 UI/UX
- Material You design
- Dynamic theming
- Intuitive interface
- Formatted response
- Dark/Light mode

## 🛠 Technologies Used
- Kotlin
- Jetpack Compose
- Material 3

## 📱 Screenshots

<div align="center">
  <table align="center" style="margin: 0 auto">
    <tr>
      <td align="center">
        <img src="imgs/screenshot/http.png" width="250" alt="HTTP Screenshot">
        <br>
      </td>
      <td align="center">
        <img src="imgs/screenshot/dns.png" width="250" alt="DNS Screenshot">
        <br>
      </td>
      <td align="center">
        <img src="imgs/screenshot/ntp.png" width="250" alt="NTP Screenshot">
        <br>
      </td>
      <td align="center">
        <img src="imgs/screenshot/custom.png" width="250" alt="Custom Screenshot">
        <br>
      </td>
    </tr>
  </table>
</div>

<p align="center">
  <em>Clean Material You design with intuitive protocol-specific controls</em>
</p>


## 🔧 Requirements
- Android 8.0 (API level 26) or higher
- Internet connection

## 📥 Installation
1. Download the latest release from GitHub
2. Install the APK on your device
3. Grant required network permissions

## 💡 How to Use
1. Select desired protocol
2. Enter destination host
3. Configure protocol-specific options
4. Press "Send" to send request
5. View response in dedicated area

## 🤝 Contributing
Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

---

⭐ If you like this project, give it a star on GitHub!
