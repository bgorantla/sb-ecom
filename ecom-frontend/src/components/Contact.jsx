import { FaBug, FaEnvelope, FaGithub, FaLinkedin } from "react-icons/fa";
import { siteInfo } from "../utils/siteInfo";

// Only the links that have a value in siteInfo are shown.
const links = [
    { icon: FaGithub, label: "GitHub", value: "@" + siteInfo.githubProfileUrl.split("/").pop(), href: siteInfo.githubProfileUrl },
    { icon: FaLinkedin, label: "LinkedIn", value: "Connect with me", href: siteInfo.linkedinUrl },
    { icon: FaEnvelope, label: "Email", value: siteInfo.email, href: siteInfo.email && `mailto:${siteInfo.email}` },
    { icon: FaBug, label: "Found a bug?", value: "Open an issue on GitHub", href: `${siteInfo.repoUrl}/issues` },
].filter((link) => link.href);

const Contact = () => {
    return (
        <div className="flex flex-col items-center px-4 py-16">
            <div className="bg-white shadow-lg rounded-lg p-8 w-full max-w-lg">
                <h1 className="text-4xl font-bold text-center mb-4 text-slate-800">Get in touch</h1>
                <p className="text-slate-600 text-center mb-8">
                    {siteInfo.storeName} is a portfolio project by {siteInfo.ownerName}. Questions,
                    feedback or opportunities are all welcome.
                </p>

                <ul className="space-y-3">
                    {links.map(({ icon: Icon, label, value, href }) => (
                        <li key={label}>
                            <a href={href} target="_blank" rel="noreferrer"
                                className="flex items-center gap-4 border border-slate-200 rounded-lg p-4 hover:bg-slate-50 transition-colors">
                                <Icon className="text-2xl text-slate-700 shrink-0" />
                                <div>
                                    <p className="font-semibold text-slate-800">{label}</p>
                                    <p className="text-sm text-slate-600">{value}</p>
                                </div>
                            </a>
                        </li>
                    ))}
                </ul>
            </div>
        </div>
    );
};

export default Contact;
