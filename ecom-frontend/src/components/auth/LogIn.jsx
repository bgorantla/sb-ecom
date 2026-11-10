import { useState } from "react";
import { useForm } from "react-hook-form";
import { AiOutlineLogin } from "react-icons/ai";
import { Link, useLocation, useNavigate } from "react-router-dom";
import InputField from "../shared/InputField";
import { useDispatch } from "react-redux";
import { authenticateSignInUser, loginAsDemoShopper } from "../../store/actions";
import toast from "react-hot-toast";
import Spinners from "../shared/Spinners";
import useDemoInfo from "../../hooks/useDemoInfo";

const LogIn = () => {
    const navigate = useNavigate();
    const location = useLocation();
    const dispatch = useDispatch();
    // Where to go after logging in: the page that sent you here (e.g. checkout), or home
    const from = location.state?.from || "/";
    const [loader, setLoader] = useState(false);
    const demo = useDemoInfo();

    const {
        register,
        handleSubmit,
        reset,
        formState: {errors},
    } = useForm({
        mode: "onTouched",
    });

    const loginHandler = async (data) => {
        console.log("Login Click");
        dispatch(authenticateSignInUser(data, toast, reset, navigate, setLoader, from));
    };

    // Demo buttons: a brand-new shopper account, or the shared read-only admin.
    // The first line tells the login-page guard (PrivateRoute) where to go once logged in.
    const tryAsShopper = () => {
        navigate(".", { replace: true, state: { from: "/products" } });
        dispatch(loginAsDemoShopper(toast, navigate, setLoader));
    };
    const tryAsAdmin = () => {
        navigate(".", { replace: true, state: { from: "/admin" } });
        dispatch(authenticateSignInUser(
            { username: demo.adminUsername, password: demo.adminPassword },
            toast, reset, navigate, setLoader, "/admin"));
    };

    return (
        <div className="min-h-[calc(100vh-64px)] flex justify-center items-center">
            <form
                onSubmit={handleSubmit(loginHandler)}
                className="sm:w-[450px] w-[360px] shadow-custom py-8 sm:px-8 px-4 rounded-md">
                    <div className="flex flex-col items-center justify-center space-y-4">
                        <AiOutlineLogin className="text-slate-800 text-5xl"/>
                        <h1 className="text-slate-800 text-center font-montserrat lg:text-3xl text-2xl font-bold">
                            Login Here
                        </h1>
                    </div>
            <hr className="mt-2 mb-5 text-black" />
            <div className="flex flex-col gap-3">
                <InputField
                    label="UserName"
                    required
                    id="username"
                    type="text"
                    message="*UserName is required"
                    placeholder="Enter your username"
                    register={register}
                    errors={errors}
                    />

                <InputField
                    label="Password"
                    required
                    id="password"
                    type="password"
                    message="*Password is required"
                    placeholder="Enter your password"
                    register={register}
                    errors={errors}
                    />
            </div>

            <button
                disabled={loader}
                className="bg-button-gradient flex gap-2 items-center justify-center font-semibold text-white w-full py-2 hover:text-slate-400 transition-colors duration-100 rounded-sm my-3"
                type="submit">
                {loader ? (
                    <>
                    <Spinners /> Loading...
                    </>
                ) : (
                    <>Login</>
                )}
            </button>

            {demo.enabled && (
                <div className="mt-6 border-t border-slate-200 pt-5">
                    <p className="text-center text-sm font-semibold text-slate-700 mb-3">
                        Just looking? Try the demo
                    </p>
                    <div className="grid grid-cols-2 gap-3">
                        <button type="button" disabled={loader} onClick={tryAsShopper}
                            className="border border-slate-300 rounded-sm py-2 text-sm font-semibold text-slate-800 hover:bg-slate-50 disabled:opacity-50">
                            Try as shopper
                        </button>
                        <button type="button" disabled={loader} onClick={tryAsAdmin}
                            className="border border-slate-300 rounded-sm py-2 text-sm font-semibold text-slate-800 hover:bg-slate-50 disabled:opacity-50">
                            Try as admin
                        </button>
                    </div>
                    <p className="text-xs text-slate-500 text-center mt-2">
                        The shopper is a fresh account just for you. The admin can see everything
                        but can&apos;t change anything.
                    </p>
                </div>
            )}

            <p className="text-center text-sm text-slate-700 mt-6">
              Don’t have an account?
              <Link
                className="font-semibold underline hover:text-black"
                to="/register">
              <span> SignUp</span></Link>  
            </p>
            </form>
        </div>
    );
}

export default LogIn;