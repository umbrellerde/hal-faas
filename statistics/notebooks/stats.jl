### A Pluto.jl notebook ###
# v0.12.21

using Markdown
using InteractiveUtils

# ╔═╡ 8fca97a6-881a-11eb-318c-7756eb127ab6
md"""
Set up the environment, import packages
"""

# ╔═╡ 790a9962-881a-11eb-2d1f-e34eabe10138
begin
	import Pkg
	Pkg.activate(mktempdir())
end

# ╔═╡ 72ea4f46-881a-11eb-0314-4343c5cad805
begin
	Pkg.add("Plots")
	#Pkg.add("CSVFiles")
	Pkg.add("DataFrames")
	#Pkg.add("CSV")
	Pkg.add("JSON")
	Pkg.add("JSONTables")
	using Plots
	#using CSVFiles
	using DataFrames
	#using CSV
	using JSON
	using JSONTables
end

# ╔═╡ 32417062-881c-11eb-0179-f99feb1bbd6d
begin
	json_file = JSON.parsefile("test.json")
	df =vcat(DataFrame.(json_file)...)
	df
end

# ╔═╡ 645ff58a-92f0-11eb-251a-832c536400bf
begin
	df.inv = convert.(DataFrame, df.inv)
	df.result = convert.(DataFrame, df.result)
end

# ╔═╡ bac20300-92f0-11eb-1b69-b3902b589fc3
jsontable(json_file)

# ╔═╡ 7f3a779c-922d-11eb-1dd4-15b9f24be0b8
begin
	min_val = minimum(df.start)
	df.start .-= min_val
	df.end .-= min_val
	for res in df.result
		res["start_computation"] -= min_val
		res["end_computation"] -= min_val
	end
end

# ╔═╡ f43eb462-9231-11eb-3e21-159df487f7fc
min_val

# ╔═╡ ec391cc2-9230-11eb-1c51-7ddb37f0d70a
[res["start_computation"] - min_val for res in df.result]

# ╔═╡ ea2b1ce0-8827-11eb-1977-d909dbe36c77
begin
	
	plot(df.start, [res["start_computation"] for res in df.result], label="start")
	plot!(df.start, [res["end_computation"] for res in df.result], label="end")
end

# ╔═╡ cd94e8ba-882d-11eb-0e46-3bc956da6f7b
scatter(df.start, [res["pid"] for res in df.result])

# ╔═╡ e8ec77d0-9217-11eb-19d1-cd1f693dfe2c
begin
	scatter(df.start, [res["pid"] for res in df.result], label="enqueued")
	scatter!([res["start_computation"] for res in df.result], [res["pid"] for res in df.result], label="started")
end

# ╔═╡ 92ed497a-9232-11eb-36b4-9d366da1c351


# ╔═╡ Cell order:
# ╟─8fca97a6-881a-11eb-318c-7756eb127ab6
# ╠═790a9962-881a-11eb-2d1f-e34eabe10138
# ╠═72ea4f46-881a-11eb-0314-4343c5cad805
# ╠═32417062-881c-11eb-0179-f99feb1bbd6d
# ╠═645ff58a-92f0-11eb-251a-832c536400bf
# ╠═bac20300-92f0-11eb-1b69-b3902b589fc3
# ╠═7f3a779c-922d-11eb-1dd4-15b9f24be0b8
# ╠═f43eb462-9231-11eb-3e21-159df487f7fc
# ╠═ec391cc2-9230-11eb-1c51-7ddb37f0d70a
# ╠═ea2b1ce0-8827-11eb-1977-d909dbe36c77
# ╠═cd94e8ba-882d-11eb-0e46-3bc956da6f7b
# ╠═e8ec77d0-9217-11eb-19d1-cd1f693dfe2c
# ╠═92ed497a-9232-11eb-36b4-9d366da1c351
