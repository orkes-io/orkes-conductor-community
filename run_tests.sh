docker rm -f  conductor_test_container
docker build -f docker/DockerfileStandalone . -t conductor_test_container
docker run -dit --name conductor_test_container -p 8899:8080 -p 4535:5000 -t conductor_test_container
COUNTER=0
MAX_TIME=120
while ! curl -s http://localhost:8899/api/metadata/workflow -o /dev/null
do
  echo "$(date) - still trying - since $COUNTER second, will wait for $MAX_TIME"
  sleep 1
  let COUNTER=COUNTER+1
  if [ $COUNTER -gt $MAX_TIME ];
  then
    echo "Exceeded wait time of $MAX_TIME seconds.  Terminating the build"
    exit 1
  fi
done
sleep 5
echo "All set - starting tests now"
./gradlew clean
./gradlew -PIntegrationTests orkes-conductor-test-harness:test
docker rm -f  conductor_test_container

