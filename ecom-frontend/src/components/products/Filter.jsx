import { Button, FormControl, InputLabel, MenuItem, Select, Tooltip } from "@mui/material";
import { useEffect, useState } from "react";
import { FiArrowDown, FiArrowUp, FiRefreshCw, FiSearch } from "react-icons/fi";
import { useLocation, useNavigate, useSearchParams } from "react-router-dom";

const Filter = ({ categories }) => {
    const [searchParams] = useSearchParams();
    const params = new URLSearchParams(searchParams);
    const pathname = useLocation().pathname;
    const navigate = useNavigate();
    
    // Category and sort order are read straight from the URL, so a filtered page can be
    // bookmarked or shared, and the back button undoes a filter change.
    const category = searchParams.get("category") || "all";
    const sortOrder = searchParams.get("sortby") || "asc";

    // The search box keeps what's being typed, and copies it to the URL after a 700 ms pause.
    // When the URL's keyword changes some other way (Clear Filter, back button), the box follows.
    const urlKeyword = searchParams.get("keyword") || "";
    const [searchTerm, setSearchTerm] = useState(urlKeyword);
    const [lastUrlKeyword, setLastUrlKeyword] = useState(urlKeyword);
    if (urlKeyword !== lastUrlKeyword) {
        setLastUrlKeyword(urlKeyword);
        setSearchTerm(urlKeyword);
    }

    useEffect(() => {
        if (searchTerm === urlKeyword) return;   // already in the URL: nothing to do
        const handler = setTimeout(() => {
            const next = new URLSearchParams(searchParams);
            if (searchTerm) {
                next.set("keyword", searchTerm);
            } else {
                next.delete("keyword");
            }
            navigate(`${pathname}?${next.toString()}`);
        }, 700);

        return () => {
            clearTimeout(handler);
        };
    }, [searchParams, searchTerm, urlKeyword, navigate, pathname]);

    const handleCategoryChange = (event) => {
        const selectedCategory = event.target.value;

        if (selectedCategory === "all") {
            params.delete("category");
        } else {
            params.set("category", selectedCategory);
        }
        navigate(`${pathname}?${params}`);
    };

    const toggleSortOrder = () => {
        params.set("sortby", sortOrder === "asc" ? "desc" : "asc");
        navigate(`${pathname}?${params}`);
    };

    const handleClearFilters = () => {
        navigate(pathname);
    };

    return (
        <div className="flex lg:flex-row flex-col-reverse lg:justify-between justify-center items-center gap-4">
            {/* SEARCH BAR */}
            <div className="relative flex items-center 2xl:w-[450px] sm:w-[420px] w-full">
                <input 
                    type="text"
                    placeholder="Search Products"
                    value={searchTerm}
                    onChange={(e) => setSearchTerm(e.target.value)}
                    className="border border-gray-400 text-slate-800 rounded-md py-2 pl-10 pr-4 w-full focus:outline-none focus:ring-2 focus:ring-[#1976d2]"/>
                <FiSearch className="absolute left-3 text-slate-800 size={20}"/>
            </div>

            {/* CATEGORY SELECTION */}
            <div className="flex sm:flex-row flex-col gap-4 items-center">
                <FormControl
                    className="text-slate-800 border-slate-700"
                    variant="outlined"
                    size="small">
                        <InputLabel id="category-select-label">Category</InputLabel>
                        <Select
                            labelId="category-select-label"
                            value={category}
                            onChange={handleCategoryChange}
                            label="Category"
                            className="min-w-[120px] text-slate-800 border-slate-700"
                         >
                            <MenuItem value="all">All</MenuItem>
                            {categories.map((item) => (
                                <MenuItem key={item.categoryId} value={item.categoryName}>
                                    {item.categoryName}
                                </MenuItem>
                            ))}
                         </Select>
                </FormControl>

                {/* SORT BUTTON & CLEAR FILTER */}
                <Tooltip title={`Sorted by price: ${sortOrder}`}>
                    <Button variant="contained" 
                        onClick={toggleSortOrder}
                        color="primary" 
                        className="flex items-center gap-2 h-10">
                        Sort By
                        {sortOrder === "asc" ? (
                            <FiArrowUp size={20} />
                        ) : (
                            <FiArrowDown size={20} />
                        )}
                        
                    </Button>
                </Tooltip>
                <button 
                className="flex items-center gap-2 bg-rose-900 text-white px-3 py-2 rounded-md transition duration-300 ease-in shadow-md focus:outline-none"
                onClick={handleClearFilters}
                >
                    <FiRefreshCw className="font-semibold" size={16}/>
                    <span className="font-semibold">Clear Filter</span>
                </button>
            </div>
        </div>
    );
}

export default Filter;