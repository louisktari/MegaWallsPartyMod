import json
import threading
import unittest
import urllib.error
import urllib.request
from partymod_service import summary, Service, serve, ServiceError

PROFILE = {"id": "00000000000000000000000000000001", "name": "Alice"}

class ServiceTest(unittest.TestCase):
    def test_missing_fields_stay_unknown(self):
        result = summary({}, PROFILE, 1700000000)
        self.assertIsNone(result["networkLevel"])
        self.assertIsNone(result["firstLogin"])
        self.assertIsNone(result["megaWalls"]["wins"])

    def test_documented_first_login_and_levels(self):
        for exp, level in ((0,1), (5000,1.5), (10000,2), (22500,3)):
            result = summary({"networkExp":exp,"firstLogin":1699999999000,"_id":"bad"}, PROFILE,1700000000)
            self.assertEqual(level,result["networkLevel"])
            self.assertEqual(1699999999000,result["firstLogin"])

    def test_bad_achievement_does_not_hide_later_entries(self):
        result=summary({"achievementsOneTime":[None,{},"walls3_legendary_cow","walls3_legendary_cow"]},PROFILE,1700000000)
        self.assertEqual(1,result["megaWalls"]["legendarySkins"])

    def test_legacy_deaths_are_fallback_not_sum(self):
        result=summary({"stats":{"Walls3":{"final_deaths":10,"finalDeaths":10}}},PROFILE,1700000000)
        self.assertEqual(10,result["megaWalls"]["finalDeaths"])

    def test_wrong_types_are_unknown(self):
        result=summary({"networkExp":"0","stats":{"Walls3":{"wins":True,"losses":-5}}},PROFILE,1700000000)
        self.assertIsNone(result["networkLevel"])
        self.assertIsNone(result["megaWalls"]["wins"])

    def test_dragon_kit_fields_are_preserved(self):
        result=summary({"stats":{"Walls3":{"classes":{"dragon":{"unlocked":True,"skill_level_d":5,"skill_level_a":5,"skill_level_b":3,"skill_level_c":3,"skill_level_g":3}}}}},PROFILE,1700000000)
        self.assertEqual(3,result["megaWalls"]["classes"]["dragon"]["gathering"])

    def test_lookup_dedupes_and_caches(self):
        class FakeService(Service):
            calls=0
            def fetch(self,url,headers=None):
                self.calls+=1
                return (PROFILE if "mojang.com" in url else {"success":True,"player":{"uuid":PROFILE["id"],"stats":{"Walls3":{"wins":3,"losses":5}}}}), {}
        service=FakeService("synthetic-key","synthetic-token")
        first=service.lookup("Alice")
        self.assertEqual(first,service.lookup("alice"))
        self.assertEqual(2,service.calls)
        self.assertNotIn("synthetic-key",json.dumps(first))

    def test_application_budget_blocks_uncached_lookup(self):
        service=Service("synthetic-key","synthetic-token",budget=1)
        import time
        service.requests.append(time.time())
        with self.assertRaises(ServiceError) as error:
            service.lookup("Alice")
        self.assertEqual(429,error.exception.status)

    def test_identity_mismatch_is_never_cached(self):
        class WrongPlayer(Service):
            def fetch(self,url,headers=None):
                return (PROFILE if "mojang.com" in url else {"success":True,"player":{"uuid":"a"*32}}), {}
        service=WrongPlayer("synthetic-key","synthetic-token")
        with self.assertRaises(ServiceError) as error:
            service.lookup("Alice")
        self.assertEqual(502,error.exception.status)
        self.assertEqual(0,len(service.cache))

    def test_connections_are_bounded_before_reading_headers(self):
        import socket
        import time
        server=serve(Service("synthetic-key","synthetic-token"),port=0)
        thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
        sockets=[]
        try:
            for _ in range(16):
                connection=socket.create_connection(server.server_address,timeout=2)
                sockets.append(connection)
                connection.sendall(b"GET /partymod/v1/players/Alice HTTP/1.0\r\n")
            time.sleep(.2)
            with socket.create_connection(server.server_address,timeout=2) as overflow:
                self.assertIn(b"503",overflow.recv(256))
        finally:
            for connection in sockets:
                connection.close()
            server.shutdown();server.server_close()

    def test_fixed_routes_and_auth(self):
        class FakeService(Service):
            def lookup(self,name):
                return summary({},PROFILE,1700000000)
        service=FakeService("synthetic-key","a-long-synthetic-access-token")
        server=serve(service,port=0)
        thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
        base="http://127.0.0.1:"+str(server.server_address[1])
        try:
            with self.assertRaises(urllib.error.HTTPError) as error:
                urllib.request.urlopen(base+"/partymod/v1/players/Alice")
            self.assertEqual(401,error.exception.code)
            request=urllib.request.Request(base+"/partymod/v1/players/Alice",headers={"Authorization":"Bearer "+service.token})
            with urllib.request.urlopen(request) as response:
                self.assertEqual("Alice",json.load(response)["name"])
            with self.assertRaises(urllib.error.HTTPError) as error:
                urllib.request.urlopen(urllib.request.Request(base+"/proxy?url=anything",headers={"Authorization":"Bearer "+service.token}))
            self.assertEqual(404,error.exception.code)
        finally:
            server.shutdown();server.server_close()

if __name__ == "__main__":
    unittest.main()
