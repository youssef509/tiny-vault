# VPS Deployment Guide (Any Provider)

This guide walks you through deploying the Storage Service to any Linux VPS (Hetzner, Contabo, DigitalOcean, Netcup, etc.) using Docker Compose.

## 1. Prepare the VPS

SSH into your VPS:
```bash
ssh root@your-vps-ip
```

Install Docker and Docker Compose (if not already installed):
```bash
# Install Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sh get-docker.sh

# Enable and start Docker
systemctl enable docker
systemctl start docker
```

## 2. Set Up Directories

Create a directory for the application and the actual storage path:
```bash
mkdir -p /opt/storage-service/storage-data
cd /opt/storage-service
```

## 3. Transfer Files (Via Git)

Since you already cloned the repository onto your server, simply navigate into the project directory:

```bash
cd /opt/storage-service/tiny-vault
```

## 4. Environment Variables

Create a `.env` file inside the `tiny-vault` directory with your production secrets (including your Neon DB connection):
```bash
nano .env
```
Paste the following, modifying the values to match your Neon database and domains:
```env
# Neon PostgreSQL Connection (or your own DB)
DB_URL=jdbc:postgresql://your-db-host.com/neondb?sslmode=require&channel_binding=require
DB_USERNAME=your_db_username
DB_PASSWORD=your_super_secret_db_password

# Application Settings
APP_BASE_URL=https://storage.yourdomain.com
CORS_ORIGINS=https://your-nextjs-app.com,https://your-laravel-app.com
```
Save and exit (`Ctrl+O`, `Enter`, `Ctrl+X`).

## 5. Build and Start

Run Docker Compose to build the Maven project into an Alpine JRE container and start it:
```bash
docker compose up -d --build
```

Check the logs to ensure it started successfully:
```bash
docker compose logs -f app
```

## 6. Initial User Setup (First Run Only)

Because `ddl-auto` is set to `validate` in production, you must run the database schema setup manually, OR temporarily switch it to `update` for the first run. However, we have a `schema.sql` file. Spring Boot automatically executes `schema.sql` on startup!

To create your first admin API key, you have two options:
1. Temporarily pass `APP_INIT_CREATE_USER=true` as an environment variable in `docker-compose.yml`, along with `APP_INIT_USER_EMAIL`, `APP_INIT_USER_API_KEY`, and `APP_INIT_USER_API_SECRET`.
2. Connect to the PostgreSQL database directly and `INSERT` a user.

### Option 1 (Environment Variables via docker-compose.yml):
```yaml
    environment:
      - APP_INIT_CREATE_USER=true
      - APP_INIT_USER_EMAIL=me@mydomain.com
      - APP_INIT_USER_API_KEY=my-prod-key-v1
      - APP_INIT_USER_API_SECRET=my-prod-secret!
```
Then restart the app: `docker compose up -d`
*(Be sure to remove these lines after the user is created to avoid leaving secrets in your compose file!)*

## 7. Nginx Reverse Proxy & SSL (Recommended)

To expose the app securely to the internet, install Nginx and Certbot:
```bash
apt install nginx certbot python3-certbot-nginx -y
```

Create an Nginx site config:
```bash
nano /etc/nginx/sites-available/storage-service
```
```nginx
server {
    server_name storage.yourdomain.com;

    # Allow large uploads (105MB)
    client_max_body_size 105M;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

enable the site and get an SSL certificate:
```bash
ln -s /etc/nginx/sites-available/storage-service /etc/nginx/sites-enabled/
nginx -t
systemctl reload nginx
certbot --nginx -d storage.yourdomain.com
```

Your service is now live, rate-limited, secured, and ready to be consumed by your Next.js and Laravel apps!
