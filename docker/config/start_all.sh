# configure and start redis
mkdir -p /redis
cd /redis
nohup redis-server /app/config/redis.conf &

# configure and start postgres
mkdir -p /pgdata
chown -R postgres:postgres /pgdata
chmod 0700 /pgdata
su postgres -c 'initdb -D /pgdata'
su postgres -c 'pg_ctl start -D /pgdata'

cat /app/config/banner.txt
nohup /app/startup.sh &
tail -f /app/logs/server.log