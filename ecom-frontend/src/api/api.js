import axios from "axios";

const api = axios.create({
    baseURL: `${import.meta.env.VITE_BACK_END_URL}/api`,
    withCredentials: true,
});

// The read-only demo admin gets a 403 with { demoReadOnly: true, message } when it tries
// to change something. Some actions read their error text from "description" or
// "categoryName" instead of "message", so copy the message into those fields too.
// That way every page shows the same explanation.
api.interceptors.response.use(undefined, (error) => {
    const data = error?.response?.data;
    if (data?.demoReadOnly) {
        data.description = data.message;
        data.categoryName = data.message;
    }
    return Promise.reject(error);
});

export default api;
