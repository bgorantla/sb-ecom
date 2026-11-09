import { useEffect, useRef, useState } from 'react'
import { FaCheckCircle, FaClock, FaTimesCircle } from 'react-icons/fa';
import { useDispatch } from 'react-redux';
import { Link, useSearchParams } from 'react-router-dom'
import { confirmStripePayment } from '../../store/actions';
import Loader from '../shared/Loader';
import { formatPrice } from '../../utils/formatPrice';

// Stripe sends the shopper here after paying, with ?payment_intent=pi_...&redirect_status=...
// The page asks our server to check the payment with Stripe and create the order, and only
// says "success" once the server has done that.
const PaymentConfirmation = () => {
    const [searchParams] = useSearchParams();
    const paymentIntentId = searchParams.get("payment_intent");
    const redirectStatus = searchParams.get("redirect_status");
    const dispatch = useDispatch();
    const canConfirm = Boolean(paymentIntentId) && redirectStatus === "succeeded";
    const [serverResult, setServerResult] = useState(null);
    const started = useRef(false);

    useEffect(() => {
        // In development React runs effects twice; one confirmation is enough
        // (the server would return the same order anyway)
        if (!canConfirm || started.current) return;
        started.current = true;
        dispatch(confirmStripePayment(paymentIntentId))
            .then((order) => setServerResult({ status: "success", order }))
            .catch((error) => setServerResult({
                status: "failed",
                message: error?.response?.data?.message || "We couldn't confirm your payment. If you were charged, please get in touch.",
            }));
    }, [dispatch, canConfirm, paymentIntentId]);

    // What Stripe's redirect says decides the page, until the server has answered
    let result;
    if (redirectStatus === "processing") {
        result = { status: "processing" };
    } else if (!canConfirm) {
        result = { status: "failed", message: "The payment didn't go through, so no order was placed." };
    } else {
        result = serverResult || { status: "checking" };
    }

    if (result.status === "checking") {
        return <Loader text="Confirming your payment..." />;
    }

    const views = {
        success: {
            icon: <FaCheckCircle size={64} className="text-green-500" />,
            title: "Order placed!",
            text: `Order #${result.order?.orderId} · ${formatPrice(result.order?.totalAmount ?? 0)}. Thank you for your purchase.`,
            link: { to: "/products", label: "Continue shopping" },
        },
        processing: {
            icon: <FaClock size={64} className="text-amber-500" />,
            title: "Payment processing",
            text: "Your bank is still processing the payment. The order will be created as soon as it completes.",
            link: { to: "/products", label: "Continue shopping" },
        },
        failed: {
            icon: <FaTimesCircle size={64} className="text-red-500" />,
            title: "No order placed",
            text: result.message,
            link: { to: "/cart", label: "Back to your cart" },
        },
    };
    const view = views[result.status];

    return (
        <div className='min-h-[60vh] flex items-center justify-center px-4'>
            <div className="p-8 rounded-lg shadow-lg text-center max-w-md mx-auto border border-gray-200">
                <div className="mb-4 flex justify-center">{view.icon}</div>
                <h2 className='text-3xl font-bold text-gray-800 mb-2'>{view.title}</h2>
                <p className="text-gray-600 mb-6">{view.text}</p>
                <Link to={view.link.to} className="inline-block bg-custom-gradient text-white px-5 py-2 rounded-md hover:opacity-90">
                    {view.link.label}
                </Link>
            </div>
        </div>
    );
}

export default PaymentConfirmation
