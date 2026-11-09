import { Link, useLocation } from "react-router-dom";
import { useSelector } from "react-redux";
import useDemoInfo from "../../hooks/useDemoInfo";

// A thin bar under the navbar that tells visitors this is a demo and how to pay.
// Only shown when the backend has demo mode on.
const DemoBanner = () => {
    const demo = useDemoInfo();
    const { user } = useSelector((state) => state.auth);
    const path = useLocation().pathname;

    // The admin area has its own notice (and a fixed sidebar that would cover this bar)
    if (!demo.enabled || path.startsWith("/admin")) return null;

    return (
        <div className="bg-amber-100 text-amber-900 text-sm">
            <div className="lg:px-14 sm:px-8 px-4 py-2 flex flex-wrap gap-x-2 gap-y-1 justify-center text-center">
                <span>
                    <strong>Demo store:</strong> payments are in Stripe test mode. Pay with card{" "}
                    <span className="font-mono font-semibold whitespace-nowrap">4242 4242 4242 4242</span>,
                    any future date and any CVC. Data resets every night.
                </span>
                {!user && (
                    <Link to="/login" className="font-semibold underline whitespace-nowrap">
                        Try it as a shopper or admin
                    </Link>
                )}
            </div>
        </div>
    );
};

export default DemoBanner;
