from locust import HttpUser, task, between

class WebsiteUser(HttpUser):
    wait_time = between(1, 2)

    @task
    def index(self):
        self.client.get(
            "/",
            headers={"Host": "test-servers:3000"}
        )
