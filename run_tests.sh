./gradlew clean build
docker rm -f  conductor_test_container
docker build -f docker/DockerfileStandalone . -t conductor_test_container
docker run -dit --name conductor_test_container -p 8899:8080 -t conductor_test_container
while ! curl -s http://localhost:8899/api/metadata/workflow -o /dev/null
do
  echo "$(date) - still trying"
  sleep 1
done
sleep 5
echo "All set - starting tests now"
./gradlew -PIntegrationTests orkes-conductor-test-harness:test
docker rm -f  conductor_test_container

