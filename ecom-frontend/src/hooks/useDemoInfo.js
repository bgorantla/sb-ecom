import { useEffect, useState } from "react";
import api from "../api/api";

// Asks the backend whether demo mode is on (GET /api/public/demo). The answer is
// fetched once and shared by every component that uses this hook.
// It looks like { enabled: true, adminUsername: "demo-admin", adminPassword: "..." }.
let request = null;

const useDemoInfo = () => {
    const [demo, setDemo] = useState({ enabled: false });

    useEffect(() => {
        if (!request) {
            request = api.get("/public/demo")
                .then(({ data }) => data)
                .catch(() => ({ enabled: false })); // backend unreachable: no demo features
        }
        let active = true;
        request.then((data) => active && setDemo(data));
        return () => { active = false; };
    }, []);

    return demo;
};

export default useDemoInfo;
