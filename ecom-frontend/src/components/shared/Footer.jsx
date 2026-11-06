import { Link } from "react-router-dom";
import { siteInfo, apiDocsUrl } from "../../utils/siteInfo";

const Footer = () => {
    return (
        <footer className="bg-custom-gradient text-slate-300 text-sm">
            <div className="lg:px-14 sm:px-8 px-4 py-6 flex flex-col sm:flex-row gap-3 sm:justify-between sm:items-center">
                <p>
                    {siteInfo.storeName} is a demo store built by{" "}
                    <a href={siteInfo.githubProfileUrl} target="_blank" rel="noreferrer" className="text-white hover:underline">
                        {siteInfo.ownerName}
                    </a>
                    .
                </p>
                <nav className="flex gap-5">
                    <Link to="/about" className="hover:text-white">About this project</Link>
                    <a href={siteInfo.repoUrl} target="_blank" rel="noreferrer" className="hover:text-white">Source code</a>
                    <a href={apiDocsUrl} target="_blank" rel="noreferrer" className="hover:text-white">API docs</a>
                </nav>
            </div>
        </footer>
    );
};

export default Footer;
