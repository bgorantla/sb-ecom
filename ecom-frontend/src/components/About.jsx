import { FaGithub, FaLinkedin, FaBook, FaUser, FaStore, FaUserShield } from "react-icons/fa";
import { siteInfo, apiDocsUrl } from "../utils/siteInfo";

const roles = [
    {
        icon: FaUser,
        title: "Shoppers",
        points: [
            "Browse, search, filter and sort the catalog",
            "Build a cart and save delivery addresses",
            "Pay with Stripe (test mode)",
        ],
    },
    {
        icon: FaStore,
        title: "Sellers",
        points: [
            "Add and edit their own products",
            "Upload product images",
            "View orders and update their status",
        ],
    },
    {
        icon: FaUserShield,
        title: "Admins",
        points: [
            "Dashboard with product, order and revenue totals",
            "Manage categories, products and sellers",
            "Update the status of any order",
        ],
    },
];

const stack = [
    {
        title: "Backend",
        items: ["Java 21", "Spring Boot 4", "Spring Security + JWT", "Spring Data JPA", "PostgreSQL", "Stripe API", "OpenAPI / Swagger"],
    },
    {
        title: "Frontend",
        items: ["React 19", "Redux Toolkit", "React Router", "Tailwind CSS", "Material UI", "Vite"],
    },
    {
        title: "Infrastructure",
        items: ["AWS EC2", "Amazon CloudFront", "Nginx", "systemd", "Bash deploy scripts"],
    },
];

// The request path, drawn left to right on wide screens and top to bottom on phones.
const architecture = [
    { name: "Browser", detail: "React single-page app" },
    { name: "CloudFront", detail: "HTTPS at the edge" },
    { name: "Nginx on EC2", detail: "Serves the React build, proxies /api" },
    { name: "Spring Boot", detail: "REST API, auth, payments" },
    { name: "PostgreSQL", detail: "Products, carts, orders" },
];

const About = () => {
    return (
        <div className="max-w-6xl mx-auto px-4 py-12 space-y-16">
            <section className="text-center space-y-4">
                <h1 className="text-slate-800 text-4xl font-bold">About this project</h1>
                <p className="text-lg text-slate-600 max-w-3xl mx-auto">
                    {siteInfo.storeName} is a full-stack online store: shoppers browse and pay, sellers
                    manage their own products, and admins run the whole catalog. It’s built with
                    Spring Boot and React and runs on AWS.
                </p>
            </section>

            <section className="space-y-6">
                <h2 className="text-slate-800 text-2xl font-bold text-center">What it does</h2>
                <div className="grid gap-6 md:grid-cols-3">
                    {roles.map(({ icon: Icon, title, points }) => (
                        <div key={title} className="border border-slate-200 rounded-lg shadow-sm p-6">
                            <div className="flex items-center gap-3 mb-4">
                                <Icon className="text-2xl text-slate-700" />
                                <h3 className="text-xl font-semibold text-slate-800">{title}</h3>
                            </div>
                            <ul className="space-y-2 text-slate-600 list-disc pl-5">
                                {points.map((point) => <li key={point}>{point}</li>)}
                            </ul>
                        </div>
                    ))}
                </div>
            </section>

            <section className="space-y-6">
                <h2 className="text-slate-800 text-2xl font-bold text-center">How it’s built</h2>
                <div className="flex flex-col lg:flex-row items-stretch justify-center gap-2">
                    {architecture.map((box, i) => (
                        <div key={box.name} className="flex flex-col lg:flex-row items-center gap-2 flex-1">
                            <div className="w-full h-full bg-custom-gradient text-white rounded-lg p-4 text-center">
                                <p className="font-semibold">{box.name}</p>
                                <p className="text-sm text-slate-300">{box.detail}</p>
                            </div>
                            {i < architecture.length - 1 && (
                                <span className="text-slate-400 text-2xl lg:rotate-0 rotate-90" aria-hidden="true">→</span>
                            )}
                        </div>
                    ))}
                </div>
                <div className="grid gap-6 md:grid-cols-3">
                    {stack.map(({ title, items }) => (
                        <div key={title}>
                            <h3 className="font-semibold text-slate-800 mb-3">{title}</h3>
                            <div className="flex flex-wrap gap-2">
                                {items.map((item) => (
                                    <span key={item} className="bg-slate-100 text-slate-700 text-sm px-3 py-1 rounded-full">
                                        {item}
                                    </span>
                                ))}
                            </div>
                        </div>
                    ))}
                </div>
            </section>

            <section className="bg-slate-50 rounded-lg p-8 text-center space-y-4">
                <h2 className="text-slate-800 text-2xl font-bold">Built by {siteInfo.ownerName}</h2>
                <p className="text-slate-600 max-w-2xl mx-auto">
                    It started as the project from the{" "}
                    <a href={siteInfo.courseUrl} className="underline" target="_blank" rel="noreferrer">
                        EmbarkX Spring Boot course
                    </a>
                    . The deployment and the changes since are listed in the README on GitHub.
                </p>
                <div className="flex flex-wrap justify-center gap-3">
                    <a href={siteInfo.repoUrl} target="_blank" rel="noreferrer"
                        className="flex items-center gap-2 bg-slate-800 text-white px-4 py-2 rounded-md hover:bg-slate-700">
                        <FaGithub /> Source code
                    </a>
                    <a href={apiDocsUrl} target="_blank" rel="noreferrer"
                        className="flex items-center gap-2 bg-white border border-slate-300 text-slate-800 px-4 py-2 rounded-md hover:bg-slate-100">
                        <FaBook /> API docs
                    </a>
                    {siteInfo.linkedinUrl && (
                        <a href={siteInfo.linkedinUrl} target="_blank" rel="noreferrer"
                            className="flex items-center gap-2 bg-white border border-slate-300 text-slate-800 px-4 py-2 rounded-md hover:bg-slate-100">
                            <FaLinkedin /> LinkedIn
                        </a>
                    )}
                </div>
            </section>
        </div>
    );
};

export default About;
